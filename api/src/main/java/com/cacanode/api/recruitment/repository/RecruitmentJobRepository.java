package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentJob;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface RecruitmentJobRepository extends JpaRepository<RecruitmentJob, UUID> {
    Optional<RecruitmentJob> findByIdAndTenantId(UUID id, UUID tenantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from RecruitmentJob j where j.id = :id and j.tenantId = :tenantId")
    Optional<RecruitmentJob> findForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
    List<RecruitmentJob> findByTenantId(UUID tenantId);
}
