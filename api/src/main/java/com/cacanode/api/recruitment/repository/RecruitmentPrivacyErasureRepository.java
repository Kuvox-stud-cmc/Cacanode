package com.cacanode.api.recruitment.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RecruitmentPrivacyErasureRepository {
    private final JdbcTemplate jdbc;

    public void cancelCandidateEmailDeliveries(UUID tenantId, UUID applicationId) {
        jdbc.update("""
                UPDATE recruitment_candidate_email_deliveries
                SET state='CANCELLED',cancelled_at=NOW(),updated_at=NOW()
                WHERE tenant_id=? AND application_id=? AND state IN ('PENDING','DISPATCHING','FAILED')
                """, tenantId, applicationId);
    }

    public void requestRecordingDeletion(UUID tenantId, UUID applicationId) {
        jdbc.update("""
                INSERT INTO recruitment_recording_operations(tenant_id,recording_id,operation_kind,operation_key)
                SELECT r.tenant_id,r.id,'DELETE_STORAGE','recording:'||r.id||':privacy-delete'
                FROM recruitment_interview_recordings r JOIN recruitment_interviews i
                  ON i.tenant_id=r.tenant_id AND i.id=r.session_id
                WHERE i.tenant_id=? AND i.application_id=? AND r.state<>'DELETED'
                ON CONFLICT DO NOTHING
                """, tenantId, applicationId);
    }

    public int pendingRecordingDeletionCount(UUID tenantId, UUID applicationId) {
        Integer value = jdbc.queryForObject("""
                SELECT count(*) FROM recruitment_interview_recordings r JOIN recruitment_interviews i
                  ON i.tenant_id=r.tenant_id AND i.id=r.session_id
                WHERE i.tenant_id=? AND i.application_id=? AND r.state<>'DELETED'
                """, Integer.class, tenantId, applicationId);
        return value == null ? 0 : value;
    }
}
