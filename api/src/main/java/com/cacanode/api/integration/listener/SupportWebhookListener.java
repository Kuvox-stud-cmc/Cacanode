package com.cacanode.api.integration.listener;

import com.cacanode.api.integration.service.WebhookService;
import com.cacanode.api.support.api.event.TicketCreatedEvent;
import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SupportWebhookListener {
    private final WebhookService webhookService;
    @Autowired(required = false)
    private ModuleEventInboxService inboxService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketCreated(TicketCreatedEvent event) {
        if (inboxService != null && !inboxService.claim("integration.webhook.ticket-created")) return;
        webhookService.enqueue(event.tenantId(), "ticket.created", event.ticketId(), event.webhookPayload());
    }
}
