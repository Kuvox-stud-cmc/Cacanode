package com.cacanode.api.notification.listener;

import com.cacanode.api.common.event.BillingActivatedEvent;
import com.cacanode.api.notification.enums.NotificationType;
import com.cacanode.api.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BillingNotificationListener {
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void activated(BillingActivatedEvent event) {
        notificationService.recordBillingNotice(
                event.tenantId(), NotificationType.BILLING_RENEWAL, "Pro subscription activated",
                "Your " + event.interval().toLowerCase() + " Pro access is paid through "
                        + event.paidThroughAt() + ".");
    }
}
