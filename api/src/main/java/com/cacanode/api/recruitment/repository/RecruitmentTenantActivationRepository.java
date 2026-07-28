package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentTenantActivation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RecruitmentTenantActivationRepository extends JpaRepository<RecruitmentTenantActivation,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RecruitmentTenantActivation a where a.tenantId=:tenantId")
    Optional<RecruitmentTenantActivation> findForUpdate(@Param("tenantId") UUID tenantId);
}
