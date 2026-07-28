package com.cacanode.api.integration.listener;

import com.cacanode.api.chat.api.event.ConversationClosedEvent;
import com.cacanode.api.chat.api.event.ConversationStartedEvent;
import com.cacanode.api.integration.service.WebhookService;
import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatWebhookListener {
    private final WebhookService webhookService;
    @Autowired(required = false)
    private ModuleEventInboxService inboxService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void started(ConversationStartedEvent event) {
        if (inboxService != null && !inboxService.claim("integration.webhook.conversation-started")) return;
        webhookService.enqueue(event.tenantId(), "conversation.started",
                event.conversationId(), event.webhookPayload());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closed(ConversationClosedEvent event) {
        if (inboxService != null && !inboxService.claim("integration.webhook.conversation-closed")) return;
        webhookService.enqueue(event.tenantId(), "conversation.closed",
                event.conversationId(), event.webhookPayload());
    }
}
