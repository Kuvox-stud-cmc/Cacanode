package com.cacanode.api.document.repository;

import com.cacanode.api.document.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Document> findByTenantIdAndKnowledgeBaseIdOrderByCreatedAtDesc(UUID tenantId, UUID knowledgeBaseId);
}
