package com.cacanode.api.billing.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V25BusinessHiringMigrationTest {
    private static final Map<String, UUID> TENANTS = new LinkedHashMap<>();
    private static String jdbcUrl;
    private static UUID userId;
    private static UUID paymentId;

    @BeforeAll
    static void migrateFromV24() throws SQLException {
        jdbcUrl = PostgresTestContainer.createDatabase("phase2_migration");
        Flyway.configure()
                .dataSource(jdbcUrl, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("24"))
                .load()
                .migrate();
        seedLegacyBillingRows();
        Flyway.configure()
                .dataSource(jdbcUrl, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void backfillsExactHiringEntitlementsWithoutChangingPlatformOrPaymentHistory() throws SQLException {
        Map<String, long[]> expected = Map.of(
                "STARTER", new long[]{1, 25, 0, 0, 52_428_800L},
                "TRIAL", new long[]{1, 25, 1_200, 5, 104_857_600L},
                "PRO", new long[]{3, 150, 3_600, 100, 1_073_741_824L},
                "ENTERPRISE", new long[]{0, 0, 0, 0, 0});

        try (Connection connection = connection()) {
            for (Map.Entry<String, UUID> entry : TENANTS.entrySet()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT catalog_version,
                               (entitlement_snapshot->>'maxMessages')::bigint,
                               (entitlement_snapshot->>'maxActiveJobs')::bigint,
                               (entitlement_snapshot->>'maxVerifiedApplications')::bigint,
                               (entitlement_snapshot->>'maxInterviewSeconds')::bigint,
                               (entitlement_snapshot->>'maxCvAnalyses')::bigint,
                               (entitlement_snapshot->>'maxRecruitmentStorageBytes')::bigint
                        FROM billing_subscriptions WHERE tenant_id = ?
                        """)) {
                    statement.setObject(1, entry.getValue());
                    try (ResultSet result = statement.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals("2026-07-23", result.getString(1));
                        assertEquals(platformMessages(entry.getKey()), result.getLong(2));
                        long[] hiring = expected.get(entry.getKey());
                        for (int index = 0; index < hiring.length; index++) {
                            assertEquals(hiring[index], result.getLong(index + 3), entry.getKey());
                        }
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT amount_vnd, catalog_version,
                           (entitlement_snapshot->>'maxMessages')::bigint,
                           (entitlement_snapshot->>'maxActiveJobs')::bigint,
                           (entitlement_snapshot->>'maxVerifiedApplications')::bigint,
                           (entitlement_snapshot->>'maxInterviewSeconds')::bigint,
                           (entitlement_snapshot->>'maxCvAnalyses')::bigint,
                           (entitlement_snapshot->>'maxRecruitmentStorageBytes')::bigint
                    FROM billing_payment_orders WHERE id = ?
                    """)) {
                statement.setObject(1, paymentId);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(999_000L, result.getLong(1));
                    assertEquals("legacy-payment", result.getString(2));
                    assertEquals(7_777L, result.getLong(3));
                    assertEquals(3L, result.getLong(4));
                    assertEquals(150L, result.getLong(5));
                    assertEquals(3_600L, result.getLong(6));
                    assertEquals(100L, result.getLong(7));
                    assertEquals(1_073_741_824L, result.getLong(8));
                }
            }
        }
    }

    @Test
    void initializesEveryNewUsageCounterToZero() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT COUNT(*) FROM usage_metrics
                     WHERE verified_application_count = 0 AND cv_analysis_count = 0 AND interview_seconds = 0
                     """)) {
            assertTrue(result.next());
            assertEquals(TENANTS.size(), result.getLong(1));
        }
    }

    @Test
    void installsRequiredConstraintsAndIndexes() throws SQLException {
        Set<String> constraints = queryNames("""
                SELECT conname FROM pg_constraint
                WHERE conname LIKE 'ck_hiring_%'
                   OR conname IN ('ck_usage_verified_applications_nonnegative',
                                  'ck_usage_cv_analyses_nonnegative',
                                  'ck_usage_interview_seconds_nonnegative',
                                  'ck_billing_subscription_hiring_entitlements',
                                  'ck_billing_payment_hiring_entitlements')
                """);
        assertTrue(constraints.containsAll(Set.of(
                "ck_usage_verified_applications_nonnegative",
                "ck_usage_cv_analyses_nonnegative",
                "ck_usage_interview_seconds_nonnegative",
                "ck_billing_subscription_hiring_entitlements",
                "ck_billing_payment_hiring_entitlements",
                "ck_hiring_consumption_kind",
                "ck_hiring_consumption_amounts",
                "ck_hiring_reservation_kind",
                "ck_hiring_reservation_state",
                "ck_hiring_reservation_amounts",
                "ck_hiring_active_job_amount",
                "ck_hiring_kind_state",
                "ck_hiring_expiry")));

        Set<String> indexes = queryNames("""
                SELECT indexname FROM pg_indexes
                WHERE indexname IN ('idx_hiring_consumption_tenant_kind_period',
                                    'idx_hiring_reservation_tenant_kind_state',
                                    'idx_hiring_reservation_expiry')
                """);
        assertEquals(Set.of(
                "idx_hiring_consumption_tenant_kind_period",
                "idx_hiring_reservation_tenant_kind_state",
                "idx_hiring_reservation_expiry"), indexes);
    }

    @Test
    void databaseRejectsInvalidCountersSnapshotsKindsStatesAndAmounts() {
        UUID tenantId = TENANTS.get("PRO");
        assertSqlRejected("UPDATE usage_metrics SET interview_seconds = -1 WHERE tenant_id = '" + tenantId + "'");
        assertSqlRejected("UPDATE billing_subscriptions SET entitlement_snapshot = entitlement_snapshot - 'maxActiveJobs' WHERE tenant_id = '" + tenantId + "'");
        assertSqlRejected(reservationInsert(tenantId, "UNKNOWN", "RESERVED", 1, "NULL"));
        assertSqlRejected(reservationInsert(tenantId, "ACTIVE_JOB", "COMMITTED", 1, "NULL"));
        assertSqlRejected(reservationInsert(tenantId, "ACTIVE_JOB", "RESERVED", 2, "NULL"));
        assertSqlRejected(reservationInsert(tenantId, "INTERVIEW_SECONDS", "RESERVED", 0, "NOW() + INTERVAL '1 hour'"));
    }

    private static void seedLegacyBillingRows() throws SQLException {
        try (Connection connection = connection()) {
            for (String plan : Set.of("STARTER", "TRIAL", "PRO", "ENTERPRISE")) {
                UUID tenantId = UUID.randomUUID();
                TENANTS.put(plan, tenantId);
                try (PreparedStatement tenant = connection.prepareStatement("""
                        INSERT INTO tenants(id, name, slug, plan, status, max_documents, max_messages,
                                            max_storage_mb, max_team_members, quota_anchor_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'ACTIVE', 10, ?, 2048, 4, ?, ?, ?)
                        """)) {
                    tenant.setObject(1, tenantId);
                    tenant.setString(2, plan + " tenant");
                    tenant.setString(3, plan.toLowerCase() + "-v25-test");
                    tenant.setString(4, plan);
                    tenant.setLong(5, platformMessages(plan));
                    Timestamp anchor = Timestamp.valueOf(LocalDateTime.of(2026, 1, 15, 9, 0));
                    tenant.setTimestamp(6, anchor);
                    tenant.setTimestamp(7, anchor);
                    tenant.setTimestamp(8, anchor);
                    tenant.executeUpdate();
                }
                String status = switch (plan) {
                    case "TRIAL" -> "TRIAL";
                    case "STARTER" -> "STARTER";
                    case "ENTERPRISE" -> "ENTERPRISE";
                    default -> "ACTIVE";
                };
                try (PreparedStatement subscription = connection.prepareStatement("""
                        INSERT INTO billing_subscriptions(
                            tenant_id, plan_code, status, billing_interval, catalog_version, quota_anchor_at,
                            trial_ends_at, paid_through_at, grace_ends_at, entitlement_snapshot)
                        VALUES (?, ?, ?, ?, 'legacy-subscription', '2026-01-15 09:00:00',
                                CASE WHEN ? = 'TRIAL' THEN '2026-01-29 09:00:00'::timestamp END,
                                CASE WHEN ? = 'PRO' THEN '2026-02-15 09:00:00'::timestamp END,
                                CASE WHEN ? = 'PRO' THEN '2026-02-18 09:00:00'::timestamp END,
                                jsonb_build_object(
                                    'maxMessages', ?, 'maxDocuments', 10, 'maxTeamMembers', 4, 'maxStorageMb', 2048,
                                    'apiAccess', true, 'webhooks', true, 'advancedAnalytics', true, 'customBranding', false))
                        """)) {
                    subscription.setObject(1, tenantId);
                    subscription.setString(2, plan);
                    subscription.setString(3, status);
                    subscription.setString(4, plan.equals("PRO") ? "MONTHLY" : null);
                    subscription.setString(5, plan);
                    subscription.setString(6, plan);
                    subscription.setString(7, plan);
                    subscription.setLong(8, platformMessages(plan));
                    subscription.executeUpdate();
                }
                try (PreparedStatement usage = connection.prepareStatement("""
                        INSERT INTO usage_metrics(tenant_id, period_year, period_month, period_start, period_end,
                                                  message_count, document_count, storage_mb_used, token_count)
                        VALUES (?, 2026, 1, '2026-01-15 09:00:00', '2026-02-15 09:00:00', 7, 2, 3, 11)
                        """)) {
                    usage.setObject(1, tenantId);
                    usage.executeUpdate();
                }
            }

            userId = UUID.randomUUID();
            try (PreparedStatement user = connection.prepareStatement("""
                    INSERT INTO users(id, tenant_id, email, password_hash, role, status)
                    VALUES (?, ?, 'billing-v25@example.com', 'hash', 'TENANT_ADMIN', 'ACTIVE')
                    """)) {
                user.setObject(1, userId);
                user.setObject(2, TENANTS.get("PRO"));
                user.executeUpdate();
            }
            paymentId = UUID.randomUUID();
            try (PreparedStatement payment = connection.prepareStatement("""
                    INSERT INTO billing_payment_orders(
                        id, tenant_id, user_id, order_code, requested_plan, billing_interval, amount_vnd,
                        catalog_version, entitlement_snapshot, expires_at, status, paid_at)
                    VALUES (?, ?, ?, 812345, 'PRO', 'MONTHLY', 999000, 'legacy-payment',
                            jsonb_build_object(
                                'maxMessages', 7777, 'maxDocuments', 44, 'maxTeamMembers', 8, 'maxStorageMb', 3333,
                                'apiAccess', true, 'webhooks', true, 'advancedAnalytics', true, 'customBranding', false),
                            '2026-02-01 00:00:00', 'PAID', '2026-01-01 00:00:00')
                    """)) {
                payment.setObject(1, paymentId);
                payment.setObject(2, TENANTS.get("PRO"));
                payment.setObject(3, userId);
                payment.executeUpdate();
            }
        }
    }

    private static long platformMessages(String plan) {
        return switch (plan) {
            case "STARTER" -> 501L;
            case "TRIAL" -> 10_001L;
            case "PRO" -> 10_002L;
            case "ENTERPRISE" -> 88_888L;
            default -> throw new IllegalArgumentException(plan);
        };
    }

    private static String reservationInsert(UUID tenantId, String kind, String state, long amount, String expiresAt) {
        return "INSERT INTO hiring_quota_reservations(tenant_id, quota_kind, aggregate_id, state, reserved_amount, expires_at) VALUES ('"
                + tenantId + "', '" + kind + "', gen_random_uuid(), '" + state + "', " + amount + ", " + expiresAt + ")";
    }

    private static void assertSqlRejected(String sql) {
        assertThrows(SQLException.class, () -> {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        });
    }

    private static Set<String> queryNames(String sql) throws SQLException {
        Set<String> names = new java.util.HashSet<>();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) names.add(result.getString(1));
        }
        return names;
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl, PostgresTestContainer.username(), PostgresTestContainer.password());
    }
}
