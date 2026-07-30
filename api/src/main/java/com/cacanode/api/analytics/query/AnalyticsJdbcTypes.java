package com.cacanode.api.analytics.query;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class AnalyticsJdbcTypes {
    private AnalyticsJdbcTypes() {
    }

    static LocalDateTime timestamp(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    static OffsetDateTime timestamptz(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
