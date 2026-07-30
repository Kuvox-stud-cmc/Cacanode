package com.cacanode.api.analytics.query;

import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import com.cacanode.api.recruitment.api.event.RecruitmentInterviewProjectionChangedEvent;
import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsProjectionListenerPostgresTest {
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        String url = PostgresTestContainer.createDatabase("analytics_projection_listener");
        Flyway.configure().dataSource(url, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                url, PostgresTestContainer.username(), PostgresTestContainer.password()));
    }

    @Test
    void bindsScheduledInterviewInstantsAsPostgresTimestamptz() {
        ModuleEventInboxService inbox = mock(ModuleEventInboxService.class);
        when(inbox.claim("analytics.recruitment-interview")).thenReturn(true);
        AnalyticsProjectionListener listener = new AnalyticsProjectionListener(jdbc, inbox);
        UUID interviewId = UUID.randomUUID();
        Instant scheduledStart = Instant.parse("2026-08-01T02:00:00Z");
        Instant scheduledEnd = Instant.parse("2026-08-01T02:30:00Z");
        LocalDateTime createdAt = LocalDateTime.parse("2026-07-30T09:00:00");
        var event = new RecruitmentInterviewProjectionChangedEvent(
                UUID.randomUUID(), interviewId, UUID.randomUUID(), UUID.randomUUID(),
                "SCHEDULED", "interview.scheduled", createdAt, createdAt.plusMinutes(1),
                createdAt.plusMinutes(1), scheduledStart, scheduledEnd, "Asia/Ho_Chi_Minh", 0,
                null, null, null, null);

        listener.recruitmentInterviewChanged(event);

        OffsetDateTime storedStart = jdbc.queryForObject("""
                SELECT scheduled_start_at FROM analytics_recruitment_interview_projection
                WHERE interview_id = ?
                """, OffsetDateTime.class, interviewId);
        OffsetDateTime storedEnd = jdbc.queryForObject("""
                SELECT scheduled_end_at FROM analytics_recruitment_interview_projection
                WHERE interview_id = ?
                """, OffsetDateTime.class, interviewId);
        assertThat(storedStart).isNotNull();
        assertThat(storedEnd).isNotNull();
        assertThat(storedStart.toInstant()).isEqualTo(scheduledStart);
        assertThat(storedEnd.toInstant()).isEqualTo(scheduledEnd);
    }
}
