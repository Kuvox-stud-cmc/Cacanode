package com.cacanode.api.recruitment.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RecruitmentActivationOperationsRepository {
    private final JdbcTemplate jdbc;

    public void requestRecordingStops(UUID tenantId) {
        jdbc.update("""
                INSERT INTO recruitment_recording_operations(tenant_id,recording_id,operation_kind,operation_key)
                SELECT tenant_id,id,'STOP','recording:'||id||':stop'
                FROM recruitment_interview_recordings
                WHERE tenant_id=? AND state IN ('START_PENDING','RECORDING')
                ON CONFLICT DO NOTHING
                """, tenantId);
    }
}
