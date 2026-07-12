package com.cacanode.api.tenant.repository;

import com.cacanode.api.tenant.model.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {
    Optional<KnowledgeBase> findByTenantIdAndSlug(UUID tenantId, String slug);
    Optional<KnowledgeBase> findByIdAndTenantId(UUID id, UUID tenantId);
}
