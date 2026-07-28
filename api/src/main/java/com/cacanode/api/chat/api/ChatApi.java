package com.cacanode.api.chat.api;

import java.util.UUID;
import java.util.List;
import java.time.LocalDateTime;

public interface ChatApi {
    ExternalConversationContext validateExternalConversation(
            UUID tenantId, UUID chatbotId, UUID integrationTokenId, UUID sessionId);

    ConversationPage projectionConversations(int page, int size);
    MessagePage projectionMessages(int page, int size);

    record ExternalConversationContext(
            UUID sessionId,
            String externalUserId,
            String channel,
            String locale
    ) {
    }

    record ConversationPage(List<ConversationSnapshot> items, boolean hasMore) {
        public ConversationPage { items = List.copyOf(items); }
    }
    record MessagePage(List<MessageSnapshot> items, boolean hasMore) {
        public MessagePage { items = List.copyOf(items); }
    }
    record ConversationSnapshot(UUID id, UUID tenantId, String channel, String status,
                                LocalDateTime createdAt, LocalDateTime closedAt,
                                LocalDateTime updatedAt) {}
    record MessageSnapshot(UUID id, UUID conversationId, UUID tenantId, String channel,
                           String role, String questionText, Long responseDurationMs,
                           int sequenceNumber, LocalDateTime createdAt) {}
}
