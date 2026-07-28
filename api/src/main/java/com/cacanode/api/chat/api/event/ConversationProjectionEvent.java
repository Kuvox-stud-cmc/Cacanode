package com.cacanode.api.chat.api.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationProjectionEvent(
        UUID conversationId,
        UUID tenantId,
        String channel,
        String status,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        LocalDateTime updatedAt
) {
}
