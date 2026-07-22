package com.cacanode.api.chat.api.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageRecordedEvent(
        UUID messageId,
        UUID conversationId,
        UUID tenantId,
        String channel,
        String role,
        String questionText,
        Long responseDurationMs,
        int sequenceNumber,
        LocalDateTime createdAt
) {
}
