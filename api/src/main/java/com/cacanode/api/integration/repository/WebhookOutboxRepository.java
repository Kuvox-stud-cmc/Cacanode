package com.cacanode.api.integration.repository;

import com.cacanode.api.integration.model.WebhookOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WebhookOutboxRepository extends JpaRepository<WebhookOutboxEvent, UUID> {
    @Query(value = """
            SELECT * FROM webhook_outbox
            WHERE status = 'PENDING' AND next_attempt_at <= NOW()
            ORDER BY created_at ASC
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookOutboxEvent> lockDueEvents();
}
