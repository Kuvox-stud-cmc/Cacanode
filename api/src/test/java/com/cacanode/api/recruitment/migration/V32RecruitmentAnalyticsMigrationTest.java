package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V32RecruitmentAnalyticsMigrationTest {
    private static String url;

    @BeforeAll static void migrate() {
        url = PostgresTestContainer.createDatabase("phase10_migration");
        Flyway.configure().dataSource(url, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void createsPrivacyMinimalRecruitmentProjectionsAndIndexes() throws Exception {
        assertTrue(names("SELECT tablename FROM pg_tables WHERE schemaname='public'").containsAll(Set.of(
                "analytics_recruitment_job_projection", "analytics_recruitment_application_projection",
                "analytics_recruitment_interview_projection")));
        Set<String> columns = names("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema='public' AND table_name LIKE 'analytics_recruitment_%_projection'
                """);
        assertFalse(columns.stream().anyMatch(Set.of("full_name", "email", "phone", "cv_content",
                "screening_answers", "transcript", "overall_score", "english_band", "storage_key")::contains));
        assertTrue(names("SELECT indexname FROM pg_indexes WHERE schemaname='public'").containsAll(Set.of(
                "idx_analytics_recruitment_job_tenant_status_time",
                "idx_analytics_recruitment_application_tenant_status_time",
                "idx_analytics_recruitment_interview_tenant_status_time")));
    }

    private static Set<String> names(String sql) throws Exception {
        Set<String> values = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(url, PostgresTestContainer.username(), PostgresTestContainer.password());
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) values.add(result.getString(1));
        }
        return values;
    }
}
