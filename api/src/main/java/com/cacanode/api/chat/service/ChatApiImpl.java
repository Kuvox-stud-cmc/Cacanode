package com.cacanode.api.chat.service;

import com.cacanode.api.chat.api.ChatApi;
import com.cacanode.api.chat.enums.ChatChannel;
import com.cacanode.api.chat.repository.ChatSessionRepository;
import com.cacanode.api.common.exception.custom.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.Duration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.cacanode.api.chat.repository.ChatMessageRepository;

@Service
@RequiredArgsConstructor
public class ChatApiImpl implements ChatApi {
    private final ChatSessionRepository repository;
    private final ChatMessageRepository messageRepository;

    @Override
    @Transactional(readOnly = true)
    public ExternalConversationContext validateExternalConversation(
            UUID tenantId, UUID chatbotId, UUID integrationTokenId, UUID sessionId) {
        var session = repository.findExternalConversation(
                        sessionId, tenantId, chatbotId, integrationTokenId,
                        List.of(ChatChannel.WIDGET, ChatChannel.CUSTOM_API))
                .orElseThrow(() -> new BadRequestException("Customer chat session was not found"));
        return new ExternalConversationContext(session.getId(), session.getExternalUserId(),
                session.getChannel().name(), session.getLocale());
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationPage projectionConversations(int page, int size) {
        var result = repository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new ConversationPage(result.stream().map(session -> new ConversationSnapshot(
                session.getId(), session.getTenantId(), session.getChannel().name(),
                session.getStatus().name(), session.getCreatedAt(), session.getClosedAt(),
                session.getUpdatedAt())).toList(), result.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public MessagePage projectionMessages(int page, int size) {
        var result = messageRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new MessagePage(result.stream().map(message -> {
            var session = repository.findById(message.getSessionId()).orElseThrow();
            Long duration = null;
            if ("assistant".equals(message.getRole())) {
                duration = messageRepository
                        .findFirstBySessionIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
                                message.getSessionId(), message.getSequenceNumber())
                        .filter(previous -> "user".equals(previous.getRole()))
                        .map(previous -> Math.max(0L, Duration.between(
                                previous.getCreatedAt(), message.getCreatedAt()).toMillis()))
                        .orElse(null);
            }
            return new MessageSnapshot(message.getId(), message.getSessionId(), message.getTenantId(),
                    session.getChannel().name(), message.getRole(),
                    "user".equals(message.getRole()) ? message.getContent() : null,
                    duration, message.getSequenceNumber(), message.getCreatedAt());
        }).toList(), result.hasNext());
    }
}
