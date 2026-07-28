package com.cacanode.api.recruitment.query;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class RecruitmentOperationsQuery {
    private final JdbcTemplate jdbc;

    public long activationTenantCount(String stage) {
        Long value = jdbc.queryForObject(
                "SELECT count(*) FROM recruitment_tenant_activation WHERE rollout_stage=?",
                Long.class,
                stage);
        return value == null ? 0 : value;
    }

    public long tenantIntegrityViolationCount() {
        Long value = jdbc.queryForObject("""
                SELECT count(*) FROM recruitment_interviews i JOIN recruitment_applications a ON a.id=i.application_id
                WHERE i.tenant_id<>a.tenant_id OR i.job_id<>a.job_id
                """, Long.class);
        return value == null ? 0 : value;
    }

    public CostInputs costInputs() {
        return new CostInputs(
                decimal("SELECT COALESCE(sum(call_duration_seconds),0) FROM recruitment_interview_call_attempts"),
                decimal("SELECT COALESCE(sum(recording_duration_seconds),0) FROM recruitment_interview_recordings"),
                decimal("SELECT COALESCE(sum(quantity),0) FROM recruitment_interview_provider_usage WHERE unit='CHARACTER'"),
                decimal("SELECT COALESCE(sum(quantity),0) FROM recruitment_interview_provider_usage WHERE unit='TOKEN'"));
    }

    private BigDecimal decimal(String sql) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    public record CostInputs(
            BigDecimal callSeconds,
            BigDecimal recordingSeconds,
            BigDecimal speechCharacters,
            BigDecimal modelTokens) {
    }
}
