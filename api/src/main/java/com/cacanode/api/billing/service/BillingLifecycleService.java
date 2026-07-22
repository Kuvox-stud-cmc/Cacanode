package com.cacanode.api.billing.service;

import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.billing.api.PaymentOrderStatus;
import com.cacanode.api.billing.query.BillingFacade;
import com.cacanode.api.billing.gateway.PaymentGatewayException;
import com.cacanode.api.billing.model.BillingPaymentOrder;
import com.cacanode.api.billing.model.BillingSubscription;
import com.cacanode.api.billing.repository.BillingPaymentOrderRepository;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.api.event.BillingNoticeEvent;
import com.cacanode.api.common.event.durable.DurableEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Slf4j(topic = "BILLING-LIFECYCLE")
@Service
@RequiredArgsConstructor
public class BillingLifecycleService {
    private static final Set<PaymentOrderStatus> OPEN =
            Set.of(PaymentOrderStatus.PENDING, PaymentOrderStatus.PROCESSING);

    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPaymentOrderRepository paymentRepository;
    private final BillingFacade facade;
    private final ApplicationEventPublisher eventPublisher;
    @Autowired(required = false)
    private DurableEventPublisher durableEventPublisher;

    @Scheduled(fixedDelayString = "${app.billing.reconciliation-interval-ms:300000}")
    public void reconcilePayments() {
        for (BillingPaymentOrder order : paymentRepository.findTop100ByStatusInOrderByCreatedAtAsc(OPEN)) {
            try {
                facade.reconcile(order.getId());
            } catch (PaymentGatewayException exception) {
                log.warn("PayOS reconciliation failed paymentId={}: {}", order.getId(), exception.getMessage());
            } catch (RuntimeException exception) {
                log.error("Payment reconciliation failed paymentId={}", order.getId(), exception);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.billing.lifecycle-interval-ms:60000}")
    @Transactional
    public void advanceSubscriptions() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<BillingSubscription> subscriptions = subscriptionRepository.findByStatusIn(
                Set.of(BillingStatus.TRIAL, BillingStatus.ACTIVE, BillingStatus.GRACE));
        for (BillingSubscription subscription : subscriptions) {
            if (subscription.getStatus() == BillingStatus.TRIAL
                    && subscription.getTrialEndsAt() != null && !now.isBefore(subscription.getTrialEndsAt())) {
                facade.moveToStarter(subscription, now);
                continue;
            }
            if (subscription.getStatus() == BillingStatus.ACTIVE
                    && subscription.getPaidThroughAt() != null && !now.isBefore(subscription.getPaidThroughAt())) {
                subscription.setStatus(BillingStatus.GRACE);
                facade.applyProjection(subscription);
            }
            if (subscription.getStatus() == BillingStatus.GRACE
                    && subscription.getGraceEndsAt() != null && !now.isBefore(subscription.getGraceEndsAt())) {
                facade.moveToStarter(subscription, now);
                continue;
            }
            sendReminderIfDue(subscription, now);
        }
    }

    private void sendReminderIfDue(BillingSubscription subscription, LocalDateTime now) {
        if (subscription.isCancelAtPeriodEnd() || subscription.getPaidThroughAt() == null) {
            return;
        }
        if (subscription.getStatus() == BillingStatus.GRACE) {
            LocalDate today = now.toLocalDate();
            if (subscription.getLastGraceReminderAt() == null
                    || subscription.getLastGraceReminderAt().toLocalDate().isBefore(today)) {
                publishBusinessEvent(new BillingNoticeEvent(subscription.getTenantId(), "BILLING_GRACE",
                        "Your Pro subscription is in grace", "Renew Pro before the grace period ends to keep Pro access."));
                subscription.setLastGraceReminderAt(now);
            }
            return;
        }
        long days = ChronoUnit.DAYS.between(now.toLocalDate(), subscription.getPaidThroughAt().toLocalDate());
        if (days == 7 && subscription.getReminder7SentAt() == null) {
            recordRenewal(subscription, now, 7);
            subscription.setReminder7SentAt(now);
        } else if (days == 3 && subscription.getReminder3SentAt() == null) {
            recordRenewal(subscription, now, 3);
            subscription.setReminder3SentAt(now);
        } else if (days == 1 && subscription.getReminder1SentAt() == null) {
            recordRenewal(subscription, now, 1);
            subscription.setReminder1SentAt(now);
        }
    }

    private void recordRenewal(BillingSubscription subscription, LocalDateTime now, int days) {
        publishBusinessEvent(new BillingNoticeEvent(subscription.getTenantId(), "BILLING_RENEWAL",
                "Pro renewal due in " + days + (days == 1 ? " day" : " days"),
                "Create a new PayOS checkout to renew your prepaid Pro subscription."));
    }

    private void publishBusinessEvent(Object event) {
        if (durableEventPublisher != null) {
            durableEventPublisher.publish("billing.notice.v1", 1, event);
        } else {
            eventPublisher.publishEvent(event);
        }
    }
}
