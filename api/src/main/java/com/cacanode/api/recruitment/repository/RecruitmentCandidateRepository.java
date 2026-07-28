package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentCandidate;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface RecruitmentCandidateRepository extends JpaRepository<RecruitmentCandidate, UUID> {
    Optional<RecruitmentCandidate> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<RecruitmentCandidate> findByTenantIdAndNormalizedEmail(UUID tenantId, String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RecruitmentCandidate c where c.id = :id and c.tenantId = :tenantId")
    Optional<RecruitmentCandidate> findForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
