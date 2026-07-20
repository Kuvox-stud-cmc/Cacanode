package com.cacanode.api.chat.service;

import com.cacanode.api.chat.enums.ChatChannel;
import com.cacanode.api.chat.model.ChatMessage;
import com.cacanode.api.chat.model.ChatSession;
import com.cacanode.api.chat.repository.ChatMessageRepository;
import com.cacanode.api.chat.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatListQueryIntegrationTest {
    @Autowired ChatControlPlaneService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatSessionRepository sessionRepository;
    @Autowired ChatMessageRepository messageRepository;

    @Test
    void playgroundCursorDoesNotSkipOrDuplicateAndTranscriptSearchIsLiteral() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        List<UUID> inserted = new ArrayList<>();
        LocalDateTime activity = LocalDateTime.parse("2026-07-20T10:00:00");
        for (int index = 0; index < 105; index++) {
            UUID sessionId = UUID.randomUUID();
            inserted.add(sessionId);
            insertSession(sessionId, tenantId, userId, "EMPLOYEE_PLAYGROUND", "OPEN",
                    activity.minusMinutes(index));
            insertMessage(sessionId, tenantId, index == 7 ? "literal 100%_ marker" : "question " + index);
        }
        UUID otherUserSession = UUID.randomUUID();
        insertSession(otherUserSession, tenantId, UUID.randomUUID(), "EMPLOYEE_PLAYGROUND", "OPEN", activity.plusMinutes(1));
        insertMessage(otherUserSession, tenantId, "question outside owner");

        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        do {
            var page = service.playgroundPage(tenantId, userId, 30, 0, cursor,
                    null, null, null, null, "activity", "desc");
            seen.addAll(page.sessions().stream().map(item -> item.id()).toList());
            cursor = page.nextCursor();
        } while (cursor != null);

        assertEquals(105, seen.size());
        assertEquals(105, new HashSet<>(seen).size());
        assertEquals(new HashSet<>(inserted), new HashSet<>(seen));

        var literal = service.playgroundPage(tenantId, userId, 30, 0, null,
                "100%_", null, null, null, "activity", "desc");
        assertEquals(1, literal.sessions().size());
        assertNull(literal.nextCursor());
    }

    @Test
    void customApiHistoryIncludesPublicEvidenceUrls() {
        UUID tenantId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setTenantId(tenantId);
        session.setChatbotId(UUID.randomUUID());
        session.setKnowledgeBaseId(UUID.randomUUID());
        session.setChannel(ChatChannel.CUSTOM_API);
        session.setIntegrationTokenId(tokenId);
        session = sessionRepository.save(session);

        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setTenantId(tenantId);
        message.setRole("assistant");
        message.setContent("See [S1]");
        message.setSequenceNumber(1);
        message.setCitations(List.of(Map.of(
                "id", "S1",
                "document_id", documentId.toString(),
                "source_name", "guide.pdf",
                "chunk_index", 0,
                "score", 0.9,
                "snippet", "Relevant evidence"
        )));
        messageRepository.save(message);

        var history = service.history(tenantId, null, tokenId, session.getId(), 50, 0);

        assertEquals(1, history.size());
        assertTrue(history.getFirst().citations().getFirst().publicUrl().startsWith(
                "http://localhost:3000/evidence/"));
    }

    private void insertSession(UUID id, UUID tenantId, UUID userId, String channel,
                               String status, LocalDateTime activity) {
        jdbc.update("""
                INSERT INTO chat_sessions (
                    id, tenant_id, user_id, chatbot_id, knowledge_base_id, locale, status,
                    channel, customer_metadata, last_activity_at, next_sequence_number,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'en-US', ?, ?, '{}', ?, 2, ?, ?)
                """, id, tenantId, userId, UUID.randomUUID(), UUID.randomUUID(), status, channel,
                Timestamp.valueOf(activity), Timestamp.valueOf(activity), Timestamp.valueOf(activity));
    }

    private void insertMessage(UUID sessionId, UUID tenantId, String content) {
        jdbc.update("""
                INSERT INTO chat_messages (
                    id, session_id, tenant_id, role, content, citations, sequence_number,
                    action, created_at
                ) VALUES (?, ?, ?, 'user', ?, '[]', 1, '{}', ?)
                """, UUID.randomUUID(), sessionId, tenantId, content,
                Timestamp.valueOf(LocalDateTime.parse("2026-07-20T10:00:00")));
    }
}
