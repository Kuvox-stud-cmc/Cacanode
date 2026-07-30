package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class V43RecruitmentProjectionEventRecoveryMigrationTest {
    private static final String INTERVIEW_EVENT_ID = "30000000-0000-4000-8000-000000000002";
    private static final String UNRELATED_EVENT_ID = "30000000-0000-4000-8000-000000000003";
    private static String url;

    @BeforeAll
    static void migrate() throws Exception {
        url = PostgresTestContainer.createDatabase("v43_recruitment_projection_recovery");
        Flyway.configure().dataSource(url, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("42"))
                .load().migrate();
        seedPoisonedEvents();
        Flyway.configure().dataSource(url, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test
    void repairsKnownPayloadsAndRequeuesOnlyAffectedProjectionEvents() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet job = statement.executeQuery("""
                    SELECT status, attempts, last_error,
                           (payload ->> 'createdAt')::TIMESTAMP =
                               (SELECT created_at FROM recruitment_jobs WHERE id = '20000000-0000-4000-8000-000000000001'),
                           (payload ->> 'updatedAt')::TIMESTAMP =
                               (SELECT updated_at FROM recruitment_jobs WHERE id = '20000000-0000-4000-8000-000000000001')
                    FROM module_event_outbox
                    WHERE event_id = '30000000-0000-4000-8000-000000000001'
                    """)) {
                assertThat(job.next()).isTrue();
                assertThat(job.getString(1)).isEqualTo("PENDING");
                assertThat(job.getInt(2)).isZero();
                assertThat(job.getString(3)).isNull();
                assertThat(job.getBoolean(4)).isTrue();
                assertThat(job.getBoolean(5)).isTrue();
            }
            assertThat(status(statement, INTERVIEW_EVENT_ID)).isEqualTo("PENDING");
            assertThat(status(statement, UNRELATED_EVENT_ID)).isEqualTo("DEAD");
        }
    }

    private static void seedPoisonedEvents() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO tenants (id, name, slug, status)
                    VALUES ('10000000-0000-4000-8000-000000000001', 'Recovery Tenant',
                            'recovery-tenant', 'ACTIVE');

                    INSERT INTO recruitment_jobs
                        (id, tenant_id, title, description, created_at, updated_at)
                    VALUES
                        ('20000000-0000-4000-8000-000000000001',
                         '10000000-0000-4000-8000-000000000001',
                         'Platform Engineer', 'Build reliable systems',
                         '2026-07-29 04:46:19', '2026-07-29 04:46:20');

                    INSERT INTO module_event_outbox
                        (event_id, event_type, event_version, payload, created_at, status,
                         attempts, next_attempt_at, last_error)
                    VALUES
                        ('30000000-0000-4000-8000-000000000001',
                         'recruitment.job.projection.v1', 1,
                         '{"tenantId":"10000000-0000-4000-8000-000000000001",\
                           "jobId":"20000000-0000-4000-8000-000000000001",\
                           "status":"DRAFT","businessEvent":null,\
                           "createdAt":null,"updatedAt":null}'::JSONB,
                         '2026-07-29 04:46:19', 'DEAD', 10, '2026-07-29 04:59:07',
                         'PreparedStatementCallback analytics_recruitment_job_projection failed'),
                        ('30000000-0000-4000-8000-000000000002',
                         'recruitment.interview.projection.v1', 1,
                         '{"scheduledStartAt":"2026-07-29T06:00:00Z",\
                           "scheduledEndAt":"2026-07-29T06:30:00Z"}'::JSONB,
                         '2026-07-29 05:34:46', 'DEAD', 10, '2026-07-29 05:47:36',
                         'PreparedStatementCallback analytics_recruitment_interview_projection failed'),
                        ('30000000-0000-4000-8000-000000000003',
                         'billing.notice.v1', 1, '{}'::JSONB,
                         '2026-07-29 06:00:00', 'DEAD', 10, '2026-07-29 06:10:00',
                         'unrelated failure');
                    """);
        }
    }

    private static String status(Statement statement, String eventId) throws Exception {
        try (ResultSet result = statement.executeQuery(
                "SELECT status FROM module_event_outbox WHERE event_id = '" + eventId + "'")) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(url, PostgresTestContainer.username(), PostgresTestContainer.password());
    }
}
