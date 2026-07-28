package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.InterviewTemplate;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface InterviewTemplateRepository extends JpaRepository<InterviewTemplate, UUID> {
    Optional<InterviewTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InterviewTemplate t where t.id = :id and t.tenantId = :tenantId")
    Optional<InterviewTemplate> findForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
