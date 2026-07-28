package com.cacanode.api.recruitment.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class RecruitmentJdbcTypes {
    private RecruitmentJdbcTypes() {
    }

    static OffsetDateTime timestamptz(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
