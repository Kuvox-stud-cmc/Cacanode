package com.cacanode.api.recruitment.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RecruitmentInterviewTransportReconciliationRepository {
    private final JdbcTemplate jdbc;

    public List<UUID> failCompletedTransportsWithoutResult() {
        return jdbc.query("""
                WITH candidates AS (
                    SELECT ca.id,ca.tenant_id,ca.interview_id
                    FROM recruitment_interview_call_attempts ca
                    JOIN recruitment_interviews i
                      ON i.tenant_id=ca.tenant_id AND i.id=ca.interview_id
                    WHERE ca.status='COMPLETED'
                      AND COALESCE(ca.next_retry_at,ca.terminal_at+INTERVAL '60 seconds')<=NOW()
                      AND i.status IN ('CONSENT_PENDING','IN_PROGRESS')
                      AND i.active_call_attempt_id IS NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM recruitment_interview_results r
                          WHERE r.tenant_id=ca.tenant_id AND r.session_id=ca.session_id
                      )
                      AND NOT EXISTS (
                          SELECT 1 FROM recruitment_interview_call_attempts newer
                          WHERE newer.tenant_id=ca.tenant_id AND newer.interview_id=ca.interview_id
                            AND newer.attempt_number>ca.attempt_number
                      )
                    ORDER BY ca.terminal_at,ca.id
                    FOR UPDATE OF ca,i SKIP LOCKED LIMIT 50
                ), failed_attempts AS (
                    UPDATE recruitment_interview_call_attempts ca
                    SET status='FAILED',failure_code='INTERVIEW_RESULT_MISSING',next_retry_at=NULL,
                        updated_at=NOW(),version=version+1
                    FROM candidates c WHERE ca.id=c.id
                    RETURNING ca.tenant_id,ca.interview_id
                ), failed_interviews AS (
                    UPDATE recruitment_interviews i
                    SET status='FAILED',active_call_attempt_id=NULL,completed_at=COALESCE(completed_at,NOW()),
                        updated_at=NOW(),version=version+1
                    FROM failed_attempts f
                    WHERE i.tenant_id=f.tenant_id AND i.id=f.interview_id
                    RETURNING i.id
                )
                SELECT id FROM failed_interviews
                """,(rs,rowNumber)->rs.getObject("id",UUID.class));
    }
}
