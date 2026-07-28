package com.cacanode.api.chat.api.event;

import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

public record ConversationStartedEvent(
        UUID tenantId, UUID conversationId, Map<String, Object> webhookPayload,
        String channel, String status, LocalDateTime createdAt) {
    public ConversationStartedEvent(UUID tenantId, UUID conversationId,
                                    Map<String, Object> webhookPayload) {
        this(tenantId, conversationId, webhookPayload,
                String.valueOf(webhookPayload.getOrDefault("channel", "WIDGET")),
                "OPEN", LocalDateTime.now());
    }
}
