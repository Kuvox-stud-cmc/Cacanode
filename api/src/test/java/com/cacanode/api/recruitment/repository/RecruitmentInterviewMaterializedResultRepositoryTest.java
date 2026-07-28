package com.cacanode.api.recruitment.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RecruitmentInterviewMaterializedResultRepositoryTest {
    @Test
    void developmentRedialDeletesOnlyMaterializedAttemptState() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        RecruitmentInterviewMaterializedResultRepository repository=
                new RecruitmentInterviewMaterializedResultRepository(jdbc);
        UUID tenantId=UUID.randomUUID(),sessionId=UUID.randomUUID();

        repository.deleteForDevelopmentRedial(tenantId,sessionId);

        verify(jdbc).update("DELETE FROM recruitment_interview_results WHERE tenant_id=? AND session_id=?",
                tenantId,sessionId);
        verify(jdbc).update("DELETE FROM recruitment_interview_transcript_turns WHERE tenant_id=? AND session_id=?",
                tenantId,sessionId);
        verify(jdbc).update("DELETE FROM recruitment_interview_provider_usage WHERE tenant_id=? AND session_id=?",
                tenantId,sessionId);
        verify(jdbc,never()).update("DELETE FROM recruitment_interview_event_inbox WHERE tenant_id=? AND session_id=?",
                tenantId,sessionId);
    }
}
