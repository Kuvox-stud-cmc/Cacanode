package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentCvAnalysis;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecruitmentCvAnalysisRepository extends JpaRepository<RecruitmentCvAnalysis,UUID> {
    Optional<RecruitmentCvAnalysis> findByIdAndTenantId(UUID id,UUID tenantId);
    Optional<RecruitmentCvAnalysis> findFirstByTenantIdAndApplicationIdOrderByCreatedAtDesc(UUID tenantId,UUID applicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RecruitmentCvAnalysis a where a.id=:id and a.tenantId=:tenantId")
    Optional<RecruitmentCvAnalysis> findForUpdate(@Param("tenantId") UUID tenantId,@Param("id") UUID id);

    @Query(value="""
            SELECT * FROM recruitment_cv_analyses
            WHERE status='QUEUED' AND publish_attempts<10 AND next_publish_at<=NOW()
            ORDER BY next_publish_at,id FOR UPDATE SKIP LOCKED LIMIT 50
            """,nativeQuery=true)
    List<RecruitmentCvAnalysis> lockDuePublication();

    @Modifying
    @Query(value="""
            UPDATE recruitment_cv_analyses SET status='CANCELLED',completed_at=NOW(),next_publish_at=NULL,
                summary=NULL,evidence='[]'::jsonb,skills='[]'::jsonb,personalized_questions='[]'::jsonb,
                failure_code='APPLICATION_CANCELLED',updated_at=NOW(),version=version+1
            WHERE tenant_id=:tenantId AND application_id=:applicationId AND status IN ('QUEUED','PUBLISHED')
            """,nativeQuery=true)
    int cancelApplication(@Param("tenantId") UUID tenantId,@Param("applicationId") UUID applicationId);

    @Modifying
    @Query(value="DELETE FROM recruitment_cv_analyses WHERE tenant_id=:tenantId AND application_id=:applicationId",nativeQuery=true)
    int deleteApplication(@Param("tenantId") UUID tenantId,@Param("applicationId") UUID applicationId);
}
