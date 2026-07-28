package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class V42PlatformJobInventoryMigrationTest {
    private static String url;

    @BeforeAll
    static void migrate() {
        url = PostgresTestContainer.createDatabase("v42_platform_job_inventory");
        Flyway.configure().dataSource(url, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test
    void createsCrossTenantLifecycleFilterAndSafeSearchIndexes() throws Exception {
        Set<String> names = new HashSet<>();
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname='public' AND tablename='recruitment_jobs' AND indexname LIKE 'idx_recruitment_jobs_platform_%'
                """); ResultSet result = statement.executeQuery()) {
            while (result.next()) names.add(result.getString(1));
        }
        assertThat(names).containsExactlyInAnyOrder(
                "idx_recruitment_jobs_platform_status_updated",
                "idx_recruitment_jobs_platform_closing",
                "idx_recruitment_jobs_platform_published",
                "idx_recruitment_jobs_platform_language_updated",
                "idx_recruitment_jobs_platform_employment_updated",
                "idx_recruitment_jobs_platform_work_mode_updated",
                "idx_recruitment_jobs_platform_department_updated",
                "idx_recruitment_jobs_platform_location_updated",
                "idx_recruitment_jobs_platform_title",
                "idx_recruitment_jobs_platform_company",
                "idx_recruitment_jobs_platform_metadata_search");
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(url, PostgresTestContainer.username(), PostgresTestContainer.password());
    }
}
