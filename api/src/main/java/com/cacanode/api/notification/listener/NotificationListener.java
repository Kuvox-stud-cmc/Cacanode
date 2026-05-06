package com.cacanode.api.notification.listener;

import com.cacanode.api.common.event.UserRegisteredEvent;
import com.cacanode.api.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j(topic = "NOTIFICATION-LISTENER")
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationService notificationService;

    @Async                  // runs in a separate thread - don't block registration
    @EventListener
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Sending confirmation email to: {}", event.getEmail());
        try {
            notificationService.sendAndRecordWelcomeEmail(
                event.getTenantId(),
                event.getUserId(),
                event.getEmail(),
                event.getFullName(),
                event.getCompanyName()
            );
        } catch (Exception e) {
            // Never let email failure break registration
            log.error("Failed to send welcome email to {}: {}", event.getEmail(), e.getMessage());
        }
    }

}
