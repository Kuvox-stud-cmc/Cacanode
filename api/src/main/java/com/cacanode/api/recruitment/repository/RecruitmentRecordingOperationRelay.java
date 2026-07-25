package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.config.RabbitRecordingOperationPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and "
        + "${app.recruitment.messaging-enabled:false} and ${app.recruitment.recording-enabled:false}")
public class RecruitmentRecordingOperationRelay {
    private static final long MAX_BACKOFF_SECONDS = 300;

    private final JdbcTemplate jdbc;
    private final RabbitRecordingOperationPublisher publisher;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.recruitment.recording.operation-publisher-delay-ms:1000}")
    @Transactional
    public void publishOne() {
        PendingNotification pending = jdbc.query("""
                SELECT id,notification_attempts
                FROM recruitment_recording_operations
                WHERE status='PENDING'
                  AND next_attempt_at<=NOW()
                  AND notification_published_at IS NULL
                  AND notification_next_attempt_at<=NOW()
                ORDER BY notification_next_attempt_at,id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """, result -> result.next()
                ? new PendingNotification(result.getObject(1, UUID.class), result.getInt(2))
                : null);
        if (pending == null) {
            return;
        }

        try {
            publisher.publish(pending.operationId());
            jdbc.update("""
                    UPDATE recruitment_recording_operations
                    SET notification_attempts=notification_attempts+1,
                        notification_published_at=?,notification_last_error_code=NULL,updated_at=NOW()
                    WHERE id=? AND status='PENDING' AND notification_published_at IS NULL
                    """, clock.instant(), pending.operationId());
        } catch (RuntimeException exception) {
            int attempts = pending.attempts() + 1;
            Instant nextAttempt = clock.instant().plusSeconds(backoffSeconds(attempts));
            jdbc.update("""
                    UPDATE recruitment_recording_operations
                    SET notification_attempts=?,notification_next_attempt_at=?,
                        notification_last_error_code=?,updated_at=NOW()
                    WHERE id=? AND status='PENDING' AND notification_published_at IS NULL
                    """, attempts, nextAttempt, errorCode(exception), pending.operationId());
        }
    }

    private static long backoffSeconds(int attempts) {
        return Math.min(MAX_BACKOFF_SECONDS, 2L * (1L << Math.min(Math.max(0, attempts - 1), 8)));
    }

    private static String errorCode(Throwable value) {
        String text = value.getMessage();
        if (text == null || text.isBlank()) {
            return value.getClass().getSimpleName();
        }
        return text.substring(0, Math.min(100, text.length()));
    }

    private record PendingNotification(UUID operationId, int attempts) {
    }
}
