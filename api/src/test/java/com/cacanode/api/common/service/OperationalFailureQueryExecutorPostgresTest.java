package com.cacanode.api.common.service;

import com.cacanode.api.billing.query.BillingOperationalFailureReader;
import com.cacanode.api.common.api.operations.OperationalFailureReadApi;
import com.cacanode.api.common.config.OperationalFailureProperties;
import com.cacanode.api.document.query.DocumentOperationalFailureReader;
import com.cacanode.api.integration.query.WebhookOperationalFailureReader;
import com.cacanode.api.recruitment.query.RecruitmentOperationalFailureReader;
import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalFailureQueryExecutorPostgresTest {
    private static final UUID TENANT = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static OperationalFailureQueryExecutor executor;

    @BeforeAll
    static void setUp() {
        String url = PostgresTestContainer.createDatabase("operational_failure_queries");
        Flyway.configure().dataSource(url, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
        var dataSource = new DriverManagerDataSource(url, PostgresTestContainer.username(),
                PostgresTestContainer.password());
        executor = new OperationalFailureQueryExecutor(new JdbcTemplate(dataSource));
    }

    @Test
    void everyOwnerQueryExecutesAgainstTheMigratedSchema() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T06:00:00Z"), ZoneOffset.UTC);
        var properties = new OperationalFailureProperties(Duration.ofMinutes(10));
        List<OperationalFailureReadApi> readers = List.of(
                new ModuleEventOperationalFailureReader(executor),
                new DocumentOperationalFailureReader(executor, properties, clock),
                new WebhookOperationalFailureReader(executor),
                new BillingOperationalFailureReader(executor),
                new RecruitmentOperationalFailureReader(executor, properties, clock));
        for (var reader : readers) for (var source : reader.sources()) {
            assertThat(reader.summary(source, Optional.empty()).total()).isZero();
            assertThat(reader.failures(source, new OperationalFailureReadApi.Query(Optional.empty(), null, null,
                    0, 20, "lastSeenAt", "desc")).items()).isEmpty();
            assertThat(reader.recent(source, Optional.empty(), 10)).isEmpty();
        }
    }

    @Test
    void exactSummaryTenantFilteringStablePaginationAndBoundedRecentAreDatabaseExecuted() {
        String sql = """
                SELECT * FROM (VALUES
                  ('00000000-0000-4000-8000-000000000001'::uuid,?::uuid,NULL::uuid,'DOCUMENT','FAILED','ERROR','DOCUMENT_PROCESSING_FAILED',1,'2026-07-28 01:00'::timestamp,'2026-07-28 03:00'::timestamp,NULL::timestamp),
                  ('00000000-0000-4000-8000-000000000002'::uuid,?::uuid,NULL::uuid,'DOCUMENT','RETRYING','WARNING','DOCUMENT_PUBLICATION_RETRY',3,'2026-07-28 02:00'::timestamp,'2026-07-28 04:00'::timestamp,'2026-07-28 05:00'::timestamp),
                  ('00000000-0000-4000-8000-000000000003'::uuid,NULL::uuid,NULL::uuid,'DOCUMENT','DEAD','CRITICAL','DOCUMENT_PUBLICATION_RETRY',8,'2026-07-28 00:00'::timestamp,'2026-07-28 02:00'::timestamp,NULL::timestamp)
                ) f(failure_id,tenant_id,resource_id,resource_type,state,severity,error_code,attempts,first_seen_at,last_seen_at,next_retry_at)
                """;
        List<?> args = List.of(TENANT, TENANT);
        var summary = executor.summary(sql, args, OperationalFailureReadApi.Source.DOCUMENT_INGESTION,
                Optional.of(TENANT));
        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.states()).containsEntry(OperationalFailureReadApi.State.FAILED, 1L)
                .containsEntry(OperationalFailureReadApi.State.RETRYING, 1L);

        var page = executor.page(sql, args, OperationalFailureReadApi.Source.DOCUMENT_INGESTION,
                new OperationalFailureReadApi.Query(Optional.of(TENANT), null, null, 0, 1, "attempts", "desc"));
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).singleElement().extracting(OperationalFailureReadApi.Failure::attempts)
                .isEqualTo(3);
        assertThat(executor.recent(sql, args, OperationalFailureReadApi.Source.DOCUMENT_INGESTION,
                Optional.empty(), 2)).hasSize(2).extracting(OperationalFailureReadApi.Failure::failureId)
                .containsExactly(UUID.fromString("00000000-0000-4000-8000-000000000002"),
                        UUID.fromString("00000000-0000-4000-8000-000000000001"));
    }
}
