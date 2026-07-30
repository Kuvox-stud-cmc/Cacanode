package com.cacanode.api.analytics.query;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsJdbcTypesTest {
    @Test
    void convertsBoundaryInstantsToExplicitUtcJdbcTypes() {
        Instant value = Instant.parse("2026-07-30T09:47:52Z");

        assertThat(AnalyticsJdbcTypes.timestamp(value))
                .isEqualTo(LocalDateTime.parse("2026-07-30T09:47:52"));
        assertThat(AnalyticsJdbcTypes.timestamptz(value))
                .isEqualTo(OffsetDateTime.parse("2026-07-30T09:47:52Z"));
        assertThat(AnalyticsJdbcTypes.timestamp(null)).isNull();
        assertThat(AnalyticsJdbcTypes.timestamptz(null)).isNull();
    }
}
