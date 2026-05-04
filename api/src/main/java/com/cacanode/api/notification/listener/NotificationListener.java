package com.cacanode.api.notification.listener;

import com.cacanode.api.common.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j(topic = "NOTIFICATION-LISTENER")
@Component
@RequiredArgsConstructor
public class NotificationListener {

    @Async
    @EventListener
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Sending confirmation email to: {}", event.getEmail());
        // TODO: call SendGrid to send confirmation email
    }

}
