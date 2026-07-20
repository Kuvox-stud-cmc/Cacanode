package com.cacanode.api.chat.service;

import com.cacanode.api.chat.enums.ChatChannel;
import com.cacanode.api.chat.enums.ChatSessionStatus;
import com.cacanode.api.chat.repository.ChatSessionRepository;
import com.cacanode.api.integration.service.WebhookService;
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
    private final WebhookService webhookService;

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
            webhookService.enqueue(session.getTenantId(), "conversation.closed", session.getId(), Map.of(
                    "conversationId", session.getId().toString(),
                    "chatbotId", session.getChatbotId().toString(),
                    "channel", session.getChannel().name(),
                    "externalUserId", session.getExternalUserId() == null ? "" : session.getExternalUserId(),
                    "reason", "idle_timeout"));
        }
    }
}
