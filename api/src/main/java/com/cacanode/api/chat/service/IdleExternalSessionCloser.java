package com.cacanode.api.chat.service;

import com.cacanode.api.chat.enums.ChatChannel;
import com.cacanode.api.chat.enums.ChatSessionStatus;
import com.cacanode.api.chat.repository.ChatSessionRepository;
import com.cacanode.api.chat.api.event.ConversationClosedEvent;
import com.cacanode.api.chat.api.event.ConversationProjectionEvent;
import com.cacanode.api.common.event.durable.DurableEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IdleExternalSessionCloser {
    private final ChatSessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    @Autowired(required = false)
    private DurableEventPublisher durableEventPublisher;

    @Value("${app.chat.idle-external-minutes:30}")
    private long idleMinutes;

    @Value("${app.chat.idle-close-batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.chat.idle-close-interval-ms:60000}")
    @Transactional
    public void closeIdleSessions() {
        var sessions = sessionRepository.findIdle(
                ChatSessionStatus.OPEN,
                List.of(ChatChannel.WIDGET, ChatChannel.CUSTOM_API),
                LocalDateTime.now().minusMinutes(idleMinutes),
                PageRequest.of(0, Math.min(Math.max(batchSize, 1), 500)));
        LocalDateTime now = LocalDateTime.now();
        for (var session : sessions) {
            session.setStatus(ChatSessionStatus.CLOSED);
            session.setClosedAt(now);
            if (durableEventPublisher != null) {
                durableEventPublisher.publish("chat.conversation.projection.v1", 1,
                        new ConversationProjectionEvent(
                                session.getId(), session.getTenantId(), session.getChannel().name(),
                                session.getStatus().name(), session.getCreatedAt(), session.getClosedAt(), now));
            }
            publishBusinessEvent(new ConversationClosedEvent(
                    session.getTenantId(), session.getId(), Map.of(
                    "conversationId", session.getId().toString(),
                    "chatbotId", session.getChatbotId().toString(),
                    "channel", session.getChannel().name(),
                    "externalUserId", session.getExternalUserId() == null ? "" : session.getExternalUserId(),
                    "reason", "idle_timeout")));
        }
    }

    private void publishBusinessEvent(Object event) {
        if (durableEventPublisher != null) {
            durableEventPublisher.publish("chat.conversation.closed.v1", 1, event);
        } else {
            eventPublisher.publishEvent(event);
        }
    }
}
