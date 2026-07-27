package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentApplication;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface RecruitmentApplicationRepository extends JpaRepository<RecruitmentApplication, UUID> {
    Optional<RecruitmentApplication> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndCandidateId(UUID tenantId, UUID candidateId);
    boolean existsByTenantIdAndJobId(UUID tenantId, UUID jobId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RecruitmentApplication a where a.id = :id and a.tenantId = :tenantId")
    Optional<RecruitmentApplication> findForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query(value="""
            SELECT * FROM recruitment_applications
            WHERE automation_outcome='PENDING' AND status='SUBMITTED'
            ORDER BY submitted_at,id FOR UPDATE SKIP LOCKED LIMIT 100
            """,nativeQuery=true)
    List<RecruitmentApplication> lockPendingAutomation();

    @Query(value="""
            SELECT a.* FROM recruitment_applications a
            JOIN recruitment_application_cvs cv ON cv.tenant_id=a.tenant_id AND cv.application_id=a.id
                AND cv.active AND cv.storage_state='PROMOTED'
            WHERE a.active_cv_analysis_id IS NULL AND a.cv_ai_mode_snapshot<>'OFF'
              AND a.cv_ai_consent_at IS NOT NULL
              AND a.status NOT IN ('INTERVIEW_COMPLETED','SHORTLISTED','REJECTED','WITHDRAWN')
            ORDER BY a.submitted_at,a.id FOR UPDATE OF a SKIP LOCKED LIMIT 100
            """,nativeQuery=true)
    List<RecruitmentApplication> lockCvAnalysisCandidates();

    @Query(value="""
            SELECT a.* FROM recruitment_applications a
            JOIN recruitment_cv_analyses analysis
              ON analysis.tenant_id=a.tenant_id AND analysis.id=a.active_cv_analysis_id
            JOIN recruitment_application_cvs cv ON cv.tenant_id=a.tenant_id AND cv.application_id=a.id
                AND cv.active AND cv.storage_state='PROMOTED'
            WHERE analysis.status='FAILED' AND analysis.failure_code IN (
                'CV_ANALYSIS_INVALID_MODEL_OUTPUT','CV_ANALYSIS_UNGROUNDED_EVIDENCE',
                'CV_ANALYSIS_INVALID_SKILL_EVIDENCE','CV_ANALYSIS_INVALID_QUESTION',
                'CV_ANALYSIS_TOO_MANY_QUESTIONS','CV_ANALYSIS_QUESTIONS_NOT_ALLOWED',
                'CV_ANALYSIS_PROTECTED_DATA_LEAKAGE','CV_ANALYSIS_RETRY_EXHAUSTED')
              AND a.cv_ai_mode_snapshot<>'OFF' AND a.cv_ai_consent_at IS NOT NULL
              AND a.status NOT IN ('INTERVIEW_COMPLETED','SHORTLISTED','REJECTED','WITHDRAWN')
            ORDER BY a.submitted_at,a.id FOR UPDATE OF a SKIP LOCKED LIMIT 100
            """,nativeQuery=true)
    List<RecruitmentApplication> lockRetryableLegacyCvAnalysisFailures();
}
