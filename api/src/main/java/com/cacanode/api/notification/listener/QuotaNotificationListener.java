package com.cacanode.api.notification.listener;

import com.cacanode.api.billing.api.event.QuotaExceededEvent;
import com.cacanode.api.billing.api.event.QuotaWarningEvent;
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
public class QuotaNotificationListener {
    private final NotificationService notificationService;
    @Autowired(required = false)
    private ModuleEventInboxService inboxService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void warning(QuotaWarningEvent event) {
        if (inboxService != null && !inboxService.claim("notification.quota-warning")) return;
        notificationService.recordBillingNotice(event.tenantId(), NotificationType.QUOTA_WARNING,
                "Message quota is at 80%", "You have used " + event.used() + " of " + event.limit()
                        + " messages in this billing period.");
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void exceeded(QuotaExceededEvent event) {
        if (inboxService != null && !inboxService.claim("notification.quota-exceeded")) return;
        notificationService.recordBillingNotice(event.tenantId(), NotificationType.QUOTA_EXCEEDED,
                "Message quota reached", "Additional messages are blocked until the next reset or plan renewal.");
    }
}
