package com.cacanode.api.notification.listener;

import com.cacanode.api.billing.api.event.BillingActivatedEvent;
import com.cacanode.api.billing.api.event.BillingNoticeEvent;
import com.cacanode.api.notification.enums.NotificationType;
import com.cacanode.api.notification.service.NotificationService;
import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BillingNotificationListener {
    private final NotificationService notificationService;
    @Autowired(required = false)
    private ModuleEventInboxService inboxService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activated(BillingActivatedEvent event) {
        if (inboxService != null && !inboxService.claim("notification.billing-activated")) return;
        notificationService.recordBillingNotice(
                event.tenantId(), NotificationType.BILLING_RENEWAL, "Pro subscription activated",
                "Your " + event.interval().toLowerCase() + " Pro access is paid through "
                        + event.paidThroughAt() + ".");
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notice(BillingNoticeEvent event) {
        if (inboxService != null && !inboxService.claim("notification.billing-notice")) return;
        notificationService.recordBillingNotice(event.tenantId(), NotificationType.valueOf(event.type()),
                event.title(), event.message());
    }
}
