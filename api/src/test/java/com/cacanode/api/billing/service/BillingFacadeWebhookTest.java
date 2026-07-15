package com.cacanode.api.billing.service;

import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.enums.BillingInterval;
import com.cacanode.api.billing.enums.BillingPlanCode;
import com.cacanode.api.billing.enums.BillingStatus;
import com.cacanode.api.billing.enums.PaymentOrderStatus;
import com.cacanode.api.billing.gateway.PaymentGateway;
import com.cacanode.api.billing.gateway.PaymentGatewayException;
import com.cacanode.api.billing.model.BillingPaymentOrder;
import com.cacanode.api.billing.model.BillingSubscription;
import com.cacanode.api.billing.repository.BillingPaymentOrderRepository;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.repository.BillingWebhookEventRepository;
import com.cacanode.api.tenant.api.TenantModuleApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillingFacadeWebhookTest {
    private BillingSubscriptionRepository subscriptions;
    private BillingPaymentOrderRepository payments;
    private BillingWebhookEventRepository webhooks;
    private TenantModuleApi tenants;
    private PaymentGateway gateway;
    private BillingFacade facade;

    @BeforeEach
    void setUp() {
        subscriptions = mock(BillingSubscriptionRepository.class);
        payments = mock(BillingPaymentOrderRepository.class);
        webhooks = mock(BillingWebhookEventRepository.class);
        tenants = mock(TenantModuleApi.class);
        gateway = mock(PaymentGateway.class);
        BillingProperties properties = new BillingProperties();
        facade = new BillingFacade(
                mock(BillingService.class), new BillingPlanCatalog(properties), properties,
                subscriptions, payments, webhooks, tenants, gateway, new BillingPeriods(),
                mock(JdbcTemplate.class), new ObjectMapper(), mock(ApplicationEventPublisher.class));
        when(webhooks.findByPayloadHash(any())).thenReturn(Optional.empty());
    }

    @Test
    void validWebhookExtendsEarlyRenewalFromExistingPaidThroughOnlyOnce() {
        UUID tenantId = UUID.randomUUID();
        BillingPaymentOrder order = order(tenantId);
        BillingSubscription subscription = subscription(tenantId);
        LocalDateTime originalPaidThrough = subscription.getPaidThroughAt();
        when(gateway.verifyWebhook(any())).thenReturn(new PaymentGateway.VerifiedWebhook(
                order.getOrderCode(), order.getAmountVnd(), "VND", order.getPaymentLinkId(), "ref-1", true));
        when(payments.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(subscriptions.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(subscription));

        facade.processPayOsWebhook(Map.of("delivery", 1));

        assertEquals(PaymentOrderStatus.PAID, order.getStatus());
        assertEquals(originalPaidThrough.plusMonths(1), subscription.getPaidThroughAt());
        assertEquals(BillingStatus.ACTIVE, subscription.getStatus());
        verify(tenants).applyEntitlements(any());

        facade.processPayOsWebhook(Map.of("delivery", 2));
        assertEquals(originalPaidThrough.plusMonths(1), subscription.getPaidThroughAt());
        verify(tenants, times(1)).applyEntitlements(any());
    }

    @Test
    void amountMismatchMovesOrderToReviewWithoutActivation() {
        UUID tenantId = UUID.randomUUID();
        BillingPaymentOrder order = order(tenantId);
        when(gateway.verifyWebhook(any())).thenReturn(new PaymentGateway.VerifiedWebhook(
                order.getOrderCode(), order.getAmountVnd() - 1, "VND", order.getPaymentLinkId(), "ref-2", true));
        when(payments.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));

        facade.processPayOsWebhook(Map.of("delivery", "mismatch"));

        assertEquals(PaymentOrderStatus.REVIEW, order.getStatus());
        verifyNoInteractions(tenants);
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
        when(payments.findByOrderCodeForUpdate(999_999L)).thenReturn(Optional.empty());

        facade.processPayOsWebhook(Map.of("delivery", "unknown"));

        verify(webhooks).save(argThat(event -> event.isSignatureValid()
                && "UNKNOWN_ORDER".equals(event.getProcessingResult())));
        verifyNoInteractions(tenants);
    }

    private BillingPaymentOrder order(UUID tenantId) {
        BillingPaymentOrder order = new BillingPaymentOrder();
        order.setTenantId(tenantId);
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
