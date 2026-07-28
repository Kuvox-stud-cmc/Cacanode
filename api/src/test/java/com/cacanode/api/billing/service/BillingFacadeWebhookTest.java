package com.cacanode.api.billing.service;

import com.cacanode.api.billing.query.BillingFacade;

import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.api.BillingDtos;
import com.cacanode.api.billing.api.BillingInterval;
import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.billing.api.PaymentOrderStatus;
import com.cacanode.api.billing.gateway.PaymentGateway;
import com.cacanode.api.billing.gateway.PaymentGatewayException;
import com.cacanode.api.billing.model.BillingPaymentOrder;
import com.cacanode.api.billing.model.BillingSubscription;
import com.cacanode.api.billing.repository.BillingPaymentOrderRepository;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.repository.BillingWebhookEventRepository;
import com.cacanode.api.tenant.api.TenantEntitlementApi;
import com.cacanode.api.tenant.api.TenantIdentityApi;
import com.cacanode.api.document.api.DocumentApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillingFacadeWebhookTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T10:15:30Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private BillingSubscriptionRepository subscriptions;
    private BillingPaymentOrderRepository payments;
    private BillingWebhookEventRepository webhooks;
    private TenantEntitlementApi entitlements;
    private TenantIdentityApi tenants;
    private PaymentGateway gateway;
    private JdbcTemplate jdbc;
    private ApplicationEventPublisher eventPublisher;
    private BillingProperties properties;
    private BillingFacade facade;

    @BeforeEach
    void setUp() {
        subscriptions = mock(BillingSubscriptionRepository.class);
        payments = mock(BillingPaymentOrderRepository.class);
        webhooks = mock(BillingWebhookEventRepository.class);
        entitlements = mock(TenantEntitlementApi.class);
        tenants = mock(TenantIdentityApi.class);
        gateway = mock(PaymentGateway.class);
        jdbc = mock(JdbcTemplate.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        properties = new BillingProperties();
        facade = new BillingFacade(
                new BillingPlanCatalog(properties), properties,
                subscriptions, payments, webhooks, entitlements, tenants, mock(DocumentApi.class),
                gateway, new BillingPeriods(),
                jdbc, new ObjectMapper(), eventPublisher, CLOCK);
        when(webhooks.findByPayloadHash(any())).thenReturn(Optional.empty());
    }

    @ParameterizedTest
    @MethodSource("selfServiceCheckouts")
    void createsProAndBusinessCheckoutWithServerPriceDescriptionAndSnapshot(
            BillingPlanCode plan, BillingInterval interval, long price) {
        UUID tenantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription(tenantId)));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(654321L);
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> {
            BillingPaymentOrder saved = invocation.getArgument(0);
            saved.setId(paymentId);
            return saved;
        });
        when(gateway.createPayment(any())).thenReturn(new PaymentGateway.CreatedPayment(
                "pay-link", "https://pay.example/checkout", NOW.plusMinutes(30)));

        BillingDtos.CheckoutResponse response = facade.createCheckout(
                tenantId, UUID.randomUUID(), new BillingDtos.CheckoutRequest(plan, interval), "checkout-key");

        assertEquals(plan, response.planCode());
        assertEquals(interval, response.interval());
        assertEquals(price, response.amountVnd());
        var request = org.mockito.ArgumentCaptor.forClass(PaymentGateway.CreatePayment.class);
        verify(gateway).createPayment(request.capture());
        assertEquals("CCN654321", request.getValue().description());
        assertEquals("CacaNode " + display(plan) + " " + interval.name().toLowerCase(), request.getValue().itemName());
        verify(payments).saveAndFlush(argThat(order -> order.getEntitlementSnapshot()
                .equals(new BillingPlanCatalog(properties).entitlements(plan))));
    }

    @ParameterizedTest
    @EnumSource(value = BillingPlanCode.class, names = {"STARTER", "TRIAL", "ENTERPRISE"})
    void rejectsPlansThatAreNotSelfService(BillingPlanCode plan) {
        UUID tenantId = UUID.randomUUID();
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription(tenantId)));

        assertThrows(com.cacanode.api.common.exception.custom.BadRequestException.class,
                () -> facade.createCheckout(tenantId, UUID.randomUUID(),
                        new BillingDtos.CheckoutRequest(plan, BillingInterval.MONTHLY), null));

        verifyNoInteractions(gateway);
    }

    @Test
    void validWebhookExtendsEarlyRenewalFromExistingPaidThroughOnlyOnce() {
        UUID tenantId = UUID.randomUUID();
        BillingPaymentOrder order = order(tenantId);
        BillingSubscription subscription = subscription(tenantId);
        LocalDateTime originalPaidThrough = subscription.getPaidThroughAt();
        when(gateway.verifyWebhook(any())).thenReturn(new PaymentGateway.VerifiedWebhook(
                order.getOrderCode(), order.getAmountVnd(), "VND", order.getPaymentLinkId(), "ref-1", true));
        when(payments.findByOrderCode(order.getOrderCode())).thenReturn(Optional.of(order));
        when(payments.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription));

        facade.processPayOsWebhook(Map.of("delivery", 1));

        assertEquals(PaymentOrderStatus.PAID, order.getStatus());
        assertEquals(originalPaidThrough.plusMonths(1), subscription.getPaidThroughAt());
        assertEquals(BillingStatus.ACTIVE, subscription.getStatus());
        verify(entitlements).applyEntitlements(any());
        verify(payments).cancelOtherOpenOrders(
                eq(tenantId), eq(order.getId()),
                eq(Set.of(PaymentOrderStatus.PENDING, PaymentOrderStatus.PROCESSING)),
                eq(PaymentOrderStatus.CANCELLED), eq("Superseded by a successful payment"), any());

        facade.processPayOsWebhook(Map.of("delivery", 2));
        assertEquals(originalPaidThrough.plusMonths(1), subscription.getPaidThroughAt());
        verify(entitlements, times(1)).applyEntitlements(any());
    }

    @Test
    void amountMismatchMovesOrderToReviewWithoutActivation() {
        UUID tenantId = UUID.randomUUID();
        BillingPaymentOrder order = order(tenantId);
        when(gateway.verifyWebhook(any())).thenReturn(new PaymentGateway.VerifiedWebhook(
                order.getOrderCode(), order.getAmountVnd() - 1, "VND", order.getPaymentLinkId(), "ref-2", true));
        when(payments.findByOrderCode(order.getOrderCode())).thenReturn(Optional.of(order));
        when(payments.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription(tenantId)));

        facade.processPayOsWebhook(Map.of("delivery", "mismatch"));

        assertEquals(PaymentOrderStatus.REVIEW, order.getStatus());
        verifyNoInteractions(tenants);
    }

    @Test
    void verifiedProToBusinessSwitchStartsFreshTermAndQuotaAnchor() {
        UUID tenantId = UUID.randomUUID();
        BillingPaymentOrder order = order(tenantId);
        order.setRequestedPlan(BillingPlanCode.BUSINESS);
        order.setAmountVnd(3_499_000L);
        order.setEntitlementSnapshot(new BillingProperties().businessEntitlements());
        BillingSubscription subscription = subscription(tenantId);
        LocalDateTime oldAnchor = subscription.getQuotaAnchorAt();
        when(gateway.verifyWebhook(any())).thenReturn(new PaymentGateway.VerifiedWebhook(
                order.getOrderCode(), order.getAmountVnd(), "VND", order.getPaymentLinkId(), "ref-business", true));
        when(payments.findByOrderCode(order.getOrderCode())).thenReturn(Optional.of(order));
        when(payments.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription));

        facade.processPayOsWebhook(Map.of("delivery", "business-switch"));

        assertEquals(BillingPlanCode.BUSINESS, subscription.getPlanCode());
        assertEquals(BillingStatus.ACTIVE, subscription.getStatus());
        assertNotEquals(oldAnchor, subscription.getQuotaAnchorAt());
        assertEquals(subscription.getQuotaAnchorAt().plusMonths(1), subscription.getPaidThroughAt());
    }

    @Test
    void businessAnnualGraceRenewalRetainsQuotaAnchorAndExtendsFromPaidThrough() {
        UUID tenantId = UUID.randomUUID();
        BillingPaymentOrder order = order(tenantId);
        order.setRequestedPlan(BillingPlanCode.BUSINESS);
        order.setBillingInterval(BillingInterval.ANNUAL);
        order.setAmountVnd(properties.getBusinessAnnualPriceVnd());
        order.setEntitlementSnapshot(properties.businessEntitlements());
        BillingSubscription subscription = subscription(tenantId);
        subscription.setPlanCode(BillingPlanCode.BUSINESS);
        subscription.setStatus(BillingStatus.GRACE);
        LocalDateTime anchor = subscription.getQuotaAnchorAt();
        LocalDateTime paidThrough = subscription.getPaidThroughAt();
        when(gateway.verifyWebhook(any())).thenReturn(new PaymentGateway.VerifiedWebhook(
                order.getOrderCode(), order.getAmountVnd(), "VND", order.getPaymentLinkId(), "ref-renew", true));
        when(payments.findByOrderCode(order.getOrderCode())).thenReturn(Optional.of(order));
        when(payments.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription));

        facade.processPayOsWebhook(Map.of("delivery", "business-annual-renewal"));

        assertEquals(anchor, subscription.getQuotaAnchorAt());
        assertEquals(paidThrough.plusYears(1), subscription.getPaidThroughAt());
        assertEquals(BillingStatus.ACTIVE, subscription.getStatus());
    }

    @Test
    void businessToProAnnualSwitchStartsFreshTermWithoutCredit() {
        UUID tenantId = UUID.randomUUID();
        BillingPaymentOrder order = order(tenantId);
        order.setBillingInterval(BillingInterval.ANNUAL);
        order.setAmountVnd(properties.getProAnnualPriceVnd());
        BillingSubscription subscription = subscription(tenantId);
        subscription.setPlanCode(BillingPlanCode.BUSINESS);
        when(gateway.verifyWebhook(any())).thenReturn(new PaymentGateway.VerifiedWebhook(
                order.getOrderCode(), order.getAmountVnd(), "VND", order.getPaymentLinkId(), "ref-switch", true));
        when(payments.findByOrderCode(order.getOrderCode())).thenReturn(Optional.of(order));
        when(payments.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription));

        facade.processPayOsWebhook(Map.of("delivery", "business-pro-switch"));

        assertEquals(BillingPlanCode.PRO, subscription.getPlanCode());
        assertEquals(NOW, subscription.getQuotaAnchorAt());
        assertEquals(NOW.plusYears(1), subscription.getPaidThroughAt());
    }

    @Test
    void lateSupersededPaidOrderMovesToReviewWithoutChangingPlan() {
        UUID tenantId = UUID.randomUUID();
        BillingPaymentOrder order = order(tenantId);
        order.setCreatedAt(LocalDateTime.now(CLOCK).minusMinutes(10));
        order.setStatus(PaymentOrderStatus.CANCELLED);
        order.setFailureReason("Superseded by a successful payment");
        BillingSubscription subscription = subscription(tenantId);
        subscription.setPlanCode(BillingPlanCode.BUSINESS);
        when(gateway.verifyWebhook(any())).thenReturn(new PaymentGateway.VerifiedWebhook(
                order.getOrderCode(), order.getAmountVnd(), "VND", order.getPaymentLinkId(), "ref-late", true));
        when(payments.findByOrderCode(order.getOrderCode())).thenReturn(Optional.of(order));
        when(payments.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription));
        when(payments.existsSupersedingPaidOrder(tenantId, order.getId(), order.getCreatedAt())).thenReturn(true);

        facade.processPayOsWebhook(Map.of("delivery", "late-superseded"));

        assertEquals(PaymentOrderStatus.REVIEW, order.getStatus());
        assertEquals(BillingPlanCode.BUSINESS, subscription.getPlanCode());
        verify(entitlements, never()).applyEntitlements(any());
    }

    @Test
    void invalidSignatureIsRejectedAndRecorded() {
        when(gateway.verifyWebhook(any())).thenThrow(new PaymentGatewayException("bad signature"));

        assertThrows(com.cacanode.api.common.exception.custom.BadRequestException.class,
                () -> facade.processPayOsWebhook(Map.of("delivery", "invalid")));

        verify(webhooks).save(argThat(event -> !event.isSignatureValid()
                && "INVALID_SIGNATURE".equals(event.getProcessingResult())));
    }

    @Test
    void unknownSignedOrderIsRecordedWithoutActivation() {
        when(gateway.verifyWebhook(any())).thenReturn(new PaymentGateway.VerifiedWebhook(
                999_999L, 1_199_000L, "VND", "missing", "ref-unknown", true));
        when(payments.findByOrderCode(999_999L)).thenReturn(Optional.empty());

        facade.processPayOsWebhook(Map.of("delivery", "unknown"));

        verify(webhooks).save(argThat(event -> event.isSignatureValid()
                && "UNKNOWN_ORDER".equals(event.getProcessingResult())));
        verifyNoInteractions(tenants);
    }

    @Test
    void paymentPollReconcilesPaidProviderOrderImmediately() {
        UUID tenantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        BillingPaymentOrder order = order(tenantId);
        order.setId(paymentId);
        BillingSubscription subscription = subscription(tenantId);
        subscription.setPlanCode(BillingPlanCode.STARTER);
        subscription.setStatus(BillingStatus.STARTER);
        subscription.setBillingInterval(null);
        subscription.setPaidThroughAt(null);
        subscription.setGraceEndsAt(null);

        when(payments.findByIdAndTenantId(paymentId, tenantId)).thenReturn(Optional.of(order));
        when(payments.findByIdAndTenantIdForUpdate(paymentId, tenantId)).thenReturn(Optional.of(order));
        when(gateway.getPayment(order.getOrderCode())).thenReturn(new PaymentGateway.ProviderPayment(
                order.getOrderCode(), order.getPaymentLinkId(), order.getAmountVnd(), order.getAmountVnd(),
                PaymentOrderStatus.PAID, "ref-poll"));
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription));

        var response = facade.payment(tenantId, paymentId);

        assertEquals(PaymentOrderStatus.PAID, response.status());
        assertEquals(BillingPlanCode.PRO, subscription.getPlanCode());
        assertEquals(BillingStatus.ACTIVE, subscription.getStatus());
        verify(entitlements).applyEntitlements(any());
    }

    @Test
    void paidProviderAmountMismatchMovesOrderToReview() {
        UUID tenantId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        BillingPaymentOrder order = order(tenantId);
        order.setId(paymentId);
        when(payments.findByIdAndTenantId(paymentId, tenantId)).thenReturn(Optional.of(order));
        when(payments.findByIdAndTenantIdForUpdate(paymentId, tenantId)).thenReturn(Optional.of(order));
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription(tenantId)));
        when(gateway.getPayment(order.getOrderCode())).thenReturn(new PaymentGateway.ProviderPayment(
                order.getOrderCode(), order.getPaymentLinkId(), order.getAmountVnd(), order.getAmountVnd() - 1,
                PaymentOrderStatus.PAID, "ref-underpaid"));

        var response = facade.payment(tenantId, paymentId);

        assertEquals(PaymentOrderStatus.REVIEW, response.status());
        verifyNoInteractions(tenants);
    }

    private BillingPaymentOrder order(UUID tenantId) {
        BillingPaymentOrder order = new BillingPaymentOrder();
        order.setId(UUID.randomUUID());
        order.setTenantId(tenantId);
        order.setUserId(UUID.randomUUID());
        order.setOrderCode(123456L);
        order.setRequestedPlan(BillingPlanCode.PRO);
        order.setBillingInterval(BillingInterval.MONTHLY);
        order.setAmountVnd(1_199_000L);
        order.setCatalogVersion("test");
        order.setEntitlementSnapshot(new BillingProperties().proEntitlements());
        order.setPaymentLinkId("link-1");
        order.setStatus(PaymentOrderStatus.PENDING);
        return order;
    }

    private static Stream<Object[]> selfServiceCheckouts() {
        return Stream.of(
                new Object[]{BillingPlanCode.PRO, BillingInterval.MONTHLY, 1_199_000L},
                new Object[]{BillingPlanCode.PRO, BillingInterval.ANNUAL, 11_990_000L},
                new Object[]{BillingPlanCode.BUSINESS, BillingInterval.MONTHLY, 3_499_000L},
                new Object[]{BillingPlanCode.BUSINESS, BillingInterval.ANNUAL, 34_990_000L});
    }

    private static String display(BillingPlanCode plan) {
        String value = plan.name().toLowerCase();
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private BillingSubscription subscription(UUID tenantId) {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setTenantId(tenantId);
        subscription.setPlanCode(BillingPlanCode.PRO);
        subscription.setStatus(BillingStatus.ACTIVE);
        subscription.setBillingInterval(BillingInterval.MONTHLY);
        subscription.setCatalogVersion("old");
        subscription.setQuotaAnchorAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        subscription.setPaidThroughAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        subscription.setGraceEndsAt(LocalDateTime.of(2026, 8, 4, 9, 0));
        subscription.setEntitlementSnapshot(new BillingProperties().proEntitlements());
        return subscription;
    }
}
