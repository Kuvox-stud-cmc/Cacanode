package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentAvailabilityException;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecruitmentAvailabilityExceptionRepository extends JpaRepository<RecruitmentAvailabilityException, UUID> {
    List<RecruitmentAvailabilityException> findByTenantIdOrderByExceptionDateAscStartLocalAsc(UUID tenantId);
    List<RecruitmentAvailabilityException> findByTenantIdAndExceptionDateBetweenOrderByExceptionDateAscStartLocalAsc(
            UUID tenantId, LocalDate from, LocalDate to);
    void deleteByTenantId(UUID tenantId);
}
