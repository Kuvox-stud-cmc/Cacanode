package com.cacanode.api.recruitment.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RecruitmentInterviewMaterializedResultRepository {
    private final JdbcTemplate jdbc;

    public void deleteForDevelopmentRedial(UUID tenantId,UUID sessionId) {
        jdbc.update("DELETE FROM recruitment_interview_results WHERE tenant_id=? AND session_id=?",
                tenantId,sessionId);
        jdbc.update("DELETE FROM recruitment_interview_transcript_turns WHERE tenant_id=? AND session_id=?",
                tenantId,sessionId);
        jdbc.update("DELETE FROM recruitment_interview_provider_usage WHERE tenant_id=? AND session_id=?",
                tenantId,sessionId);
    }
}
