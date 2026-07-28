package com.cacanode.api.recruitment.repository;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecruitmentJdbcTypesTest {
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void connect() {
        String url = PostgresTestContainer.createDatabase("recruitment_jdbc_types");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                url, PostgresTestContainer.username(), PostgresTestContainer.password()));
        jdbc.execute("CREATE TABLE recruitment_jdbc_timestamp_test(value TIMESTAMPTZ NOT NULL)");
    }

    @Test
    void bindsInstantAsPostgresTimestamptz() {
        Instant expected = Instant.parse("2026-07-27T03:33:30.123456Z");

        jdbc.update("INSERT INTO recruitment_jdbc_timestamp_test(value) VALUES (?)",
                RecruitmentJdbcTypes.timestamptz(expected));

        OffsetDateTime stored = jdbc.queryForObject(
                "SELECT value FROM recruitment_jdbc_timestamp_test", OffsetDateTime.class);
        assertEquals(expected, stored.toInstant());
    }
}
