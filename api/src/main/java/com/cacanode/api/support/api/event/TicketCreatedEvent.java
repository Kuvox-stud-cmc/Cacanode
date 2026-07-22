package com.cacanode.api.support.api.event;

import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

public record TicketCreatedEvent(
        UUID tenantId,
        UUID ticketId,
        UUID conversationId,
        UUID chatbotId,
        String tenantName,
        String customerEmail,
        String customerName,
        String title,
        String description,
        String status,
        String locale,
        Map<String, Object> webhookPayload,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public TicketCreatedEvent(UUID tenantId, UUID ticketId, UUID conversationId, UUID chatbotId,
                              String tenantName, String customerEmail, String customerName,
                              String title, String description, String status, String locale,
                              Map<String, Object> webhookPayload) {
        this(tenantId, ticketId, conversationId, chatbotId, tenantName, customerEmail,
                customerName, title, description, status, locale, webhookPayload,
                "NORMAL", LocalDateTime.now(), LocalDateTime.now());
    }

    public TicketCreatedEvent(Object source, UUID tenantId, UUID ticketId, String tenantName,
                              String customerEmail, String customerName, String title,
                              String description, String locale) {
        this(tenantId, ticketId, null, null, tenantName, customerEmail, customerName,
                title, description, "OPEN", locale, Map.of(), "NORMAL",
                LocalDateTime.now(), LocalDateTime.now());
    }

    public UUID getTicketId() { return ticketId; }
    public String getCustomerEmail() { return customerEmail; }
    public String getLocale() { return locale; }
}
