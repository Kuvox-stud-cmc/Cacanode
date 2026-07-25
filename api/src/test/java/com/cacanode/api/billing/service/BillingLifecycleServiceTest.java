package com.cacanode.api.billing.service;

import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.billing.api.PaymentOrderStatus;
import com.cacanode.api.billing.model.BillingPaymentOrder;
import com.cacanode.api.billing.model.BillingSubscription;
import com.cacanode.api.billing.query.BillingFacade;
import com.cacanode.api.billing.repository.BillingPaymentOrderRepository;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingLifecycleServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T10:15:30Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private BillingSubscriptionRepository subscriptions;
    private BillingPaymentOrderRepository payments;
    private BillingFacade facade;
    private BillingLifecycleService service;

    @BeforeEach
    void setUp() {
        subscriptions = mock(BillingSubscriptionRepository.class);
        payments = mock(BillingPaymentOrderRepository.class);
        facade = mock(BillingFacade.class);
        service = new BillingLifecycleService(
                subscriptions, payments, facade, mock(ApplicationEventPublisher.class), CLOCK);
    }

    @Test
    void advancesTrialActiveAndGraceAtTheExactUtcBoundary() {
        BillingSubscription trial = subscription(BillingPlanCode.TRIAL, BillingStatus.TRIAL);
        trial.setTrialEndsAt(NOW);
        BillingSubscription active = subscription(BillingPlanCode.BUSINESS, BillingStatus.ACTIVE);
        active.setPaidThroughAt(NOW);
        active.setGraceEndsAt(NOW.plusDays(3));
        BillingSubscription grace = subscription(BillingPlanCode.PRO, BillingStatus.GRACE);
        grace.setPaidThroughAt(NOW.minusDays(3));
        grace.setGraceEndsAt(NOW);
        when(subscriptions.findByStatusIn(any())).thenReturn(List.of(trial, active, grace));

        service.advanceSubscriptions();

        verify(facade).moveToStarter(trial, NOW);
        assertEquals(BillingStatus.GRACE, active.getStatus());
        verify(facade).applyProjection(active);
        verify(facade).moveToStarter(grace, NOW);
    }

    @Test
    void reconciliationUsesTheInjectedUtcClockAsItsCutoff() {
        BillingPaymentOrder order = new BillingPaymentOrder();
        when(payments.findTop100ByStatusInAndExpiresAtAfterOrderByCreatedAtAsc(
                eq(Set.of(PaymentOrderStatus.PENDING, PaymentOrderStatus.PROCESSING, PaymentOrderStatus.CANCELLED)),
                eq(NOW))).thenReturn(List.of(order));

        service.reconcilePayments();

        verify(facade).reconcile(order.getId());
    }

    private BillingSubscription subscription(BillingPlanCode plan, BillingStatus status) {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setPlanCode(plan);
        subscription.setStatus(status);
        subscription.setQuotaAnchorAt(NOW.minusMonths(1));
        return subscription;
    }
}
