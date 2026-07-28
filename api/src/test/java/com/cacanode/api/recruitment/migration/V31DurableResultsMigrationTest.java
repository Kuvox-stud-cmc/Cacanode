package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class V31DurableResultsMigrationTest {
    private static String url;

    @BeforeAll static void migrate() {
        url=PostgresTestContainer.createDatabase("phase9_migration");
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void createsAuthoritativeResultsUsageAndRecordingTables() throws Exception {
        assertTrue(names("SELECT tablename FROM pg_tables WHERE schemaname='public'").containsAll(Set.of(
                "recruitment_interview_event_inbox","recruitment_interview_transcript_turns",
                "recruitment_interview_results","recruitment_interview_section_results",
                "recruitment_interview_question_results","recruitment_interview_score_evaluations",
                "recruitment_interview_provider_usage","recruitment_interview_recordings",
                "recruitment_recording_operations")));
        assertTrue(names("SELECT indexname FROM pg_indexes WHERE schemaname='public'").containsAll(Set.of(
                "idx_recruitment_transcript_order","idx_recruitment_result_delivery",
                "idx_recruitment_recording_retention","idx_recruitment_recording_operations_due",
                "idx_recruitment_recording_notifications_due")));
        assertTrue(names("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema='public' AND table_name='recruitment_recording_operations'
                """).containsAll(Set.of("notification_attempts","notification_next_attempt_at",
                "notification_published_at","notification_last_error_code")));
    }

    @Test void tenantSafeForeignKeysAndImmutableTerminalUniquenessExist() throws Exception {
        Set<String> constraints=names("""
                SELECT conname FROM pg_constraint WHERE conrelid IN (
                    'recruitment_interview_event_inbox'::regclass,
                    'recruitment_interview_transcript_turns'::regclass,
                    'recruitment_interview_results'::regclass,
                    'recruitment_interview_score_evaluations'::regclass,
                    'recruitment_interview_recordings'::regclass,
                    'recruitment_recording_operations'::regclass)
                """);
        assertTrue(constraints.containsAll(Set.of("fk_recruitment_event_attempt",
                "fk_recruitment_transcript_attempt","fk_recruitment_result_attempt",
                "fk_recruitment_evaluation_turn","fk_recruitment_recording_attempt",
                "fk_recruitment_recording_operation","uq_recruitment_result_tenant_id",
                "uq_recruitment_recording_attempt")));
    }

    private static Set<String> names(String sql)throws Exception {Set<String> values=new HashSet<>();
        try(Connection c=DriverManager.getConnection(url,PostgresTestContainer.username(),PostgresTestContainer.password());
            Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())values.add(r.getString(1));}
        return values;}
}
