package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.InterviewTemplateRevision;
import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewTemplateRevisionRepository extends Repository<InterviewTemplateRevision, UUID> {
    InterviewTemplateRevision save(InterviewTemplateRevision revision);
    Optional<InterviewTemplateRevision> findByIdAndTenantId(UUID id, UUID tenantId);
    List<InterviewTemplateRevision> findByTenantIdAndTemplateIdOrderByRevisionNumberDesc(UUID tenantId, UUID templateId);
    Optional<InterviewTemplateRevision> findFirstByTenantIdAndTemplateIdOrderByRevisionNumberDesc(UUID tenantId, UUID templateId);
}
