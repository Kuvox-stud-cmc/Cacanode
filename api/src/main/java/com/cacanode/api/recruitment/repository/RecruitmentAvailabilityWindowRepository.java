package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentAvailabilityWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RecruitmentAvailabilityWindowRepository extends JpaRepository<RecruitmentAvailabilityWindow, UUID> {
    boolean existsByTenantId(UUID tenantId);
    List<RecruitmentAvailabilityWindow> findByTenantIdOrderByDayOfWeekAscStartLocalAsc(UUID tenantId);
    void deleteByTenantId(UUID tenantId);
}
