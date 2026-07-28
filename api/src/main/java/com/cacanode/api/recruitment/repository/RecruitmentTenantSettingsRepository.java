package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentTenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface RecruitmentTenantSettingsRepository extends JpaRepository<RecruitmentTenantSettings, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RecruitmentTenantSettings s where s.tenantId = :tenantId")
    Optional<RecruitmentTenantSettings> findForUpdate(@Param("tenantId") UUID tenantId);
}
