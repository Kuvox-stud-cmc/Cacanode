package com.cacanode.api.chat.api.event;

import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

public record ConversationClosedEvent(
        UUID tenantId, UUID conversationId, Map<String, Object> webhookPayload,
        LocalDateTime closedAt) {
    public ConversationClosedEvent(UUID tenantId, UUID conversationId,
                                   Map<String, Object> webhookPayload) {
        this(tenantId, conversationId, webhookPayload, LocalDateTime.now());
    }
}
