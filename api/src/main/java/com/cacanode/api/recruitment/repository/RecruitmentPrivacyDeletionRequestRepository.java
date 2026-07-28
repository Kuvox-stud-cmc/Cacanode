package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentEnums.PrivacyDeletionStatus;
import com.cacanode.api.recruitment.model.RecruitmentPrivacyDeletionRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecruitmentPrivacyDeletionRequestRepository extends JpaRepository<RecruitmentPrivacyDeletionRequest,UUID> {
    List<RecruitmentPrivacyDeletionRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId,Pageable page);
    Optional<RecruitmentPrivacyDeletionRequest> findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(UUID applicationId,PrivacyDeletionStatus status);
    @Query(value="""
            SELECT * FROM recruitment_privacy_deletion_requests
            WHERE status IN ('PENDING','RETRY') AND next_attempt_at<=:now
            ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED LIMIT 25
            """,nativeQuery=true)
    List<RecruitmentPrivacyDeletionRequest> lockDue(@Param("now") Instant now);
}
