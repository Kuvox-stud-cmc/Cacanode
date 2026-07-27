package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.config.RabbitRecordingOperationPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecruitmentRecordingOperationRelayTest {
    private static final Instant NOW = Instant.parse("2026-07-24T09:00:00Z");
    private static final UUID OPERATION_ID =
            UUID.fromString("350bcaa8-4817-5f23-9fb9-458b99e6b2e8");

    @Test
    void marksNotificationPublishedOnlyAfterConfirmedPublication() throws Exception {
        JdbcTemplate jdbc = pendingOperation(0);
        RabbitRecordingOperationPublisher publisher = mock(RabbitRecordingOperationPublisher.class);
        RecruitmentRecordingOperationRelay relay = new RecruitmentRecordingOperationRelay(
                jdbc, publisher, Clock.fixed(NOW, ZoneOffset.UTC));

        relay.publishOne();

        verify(publisher).publish(OPERATION_ID);
        verify(jdbc).update(contains("notification_published_at=?"),
                eq(OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC)),eq(OPERATION_ID));
    }

    @Test
    void uncertainPublicationRemainsUnconfirmedAndIsBackedOff() throws Exception {
        JdbcTemplate jdbc = pendingOperation(0);
        RabbitRecordingOperationPublisher publisher = mock(RabbitRecordingOperationPublisher.class);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(OPERATION_ID);
        RecruitmentRecordingOperationRelay relay = new RecruitmentRecordingOperationRelay(
                jdbc, publisher, Clock.fixed(NOW, ZoneOffset.UTC));

        relay.publishOne();

        verify(jdbc).update(contains("notification_next_attempt_at=?"),
                eq(1),eq(OffsetDateTime.ofInstant(NOW.plusSeconds(2),ZoneOffset.UTC)),
                eq("broker unavailable"),eq(OPERATION_ID));
    }

    @SuppressWarnings("unchecked")
    private static JdbcTemplate pendingOperation(int attempts) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet result = mock(ResultSet.class);
        when(result.next()).thenReturn(true);
        when(result.getObject(1, UUID.class)).thenReturn(OPERATION_ID);
        when(result.getInt(2)).thenReturn(attempts);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class))).thenAnswer(invocation ->
                ((ResultSetExtractor<Object>) invocation.getArgument(1)).extractData(result));
        return jdbc;
    }
}
