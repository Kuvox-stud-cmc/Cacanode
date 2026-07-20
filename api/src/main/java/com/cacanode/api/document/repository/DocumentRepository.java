package com.cacanode.api.document.repository;

import com.cacanode.api.document.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentVisibility;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {
    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Document> findByIdAndTenantIdAndKnowledgeBaseIdAndStatusAndVisibility(
            UUID id,
            UUID tenantId,
            UUID knowledgeBaseId,
            DocumentStatus status,
            DocumentVisibility visibility
    );

    List<Document> findByTenantIdAndKnowledgeBaseIdOrderByCreatedAtDescIdDesc(
            UUID tenantId,
            UUID knowledgeBaseId
    );

    long countByTenantIdAndStatusNot(UUID tenantId, DocumentStatus status);

    @Query("select coalesce(sum(d.fileSizeBytes), 0) from Document d where d.tenantId = :tenantId and d.status <> :status")
    long sumFileSizeByTenantIdAndStatusNot(@Param("tenantId") UUID tenantId, @Param("status") DocumentStatus status);

    List<Document> findByTenantIdAndKnowledgeBaseIdAndStatus(
            UUID tenantId, UUID knowledgeBaseId, DocumentStatus status);

    List<Document> findByTenantIdAndKnowledgeBaseIdAndStatusAndVisibility(
            UUID tenantId, UUID knowledgeBaseId, DocumentStatus status, DocumentVisibility visibility);

    List<Document> findByIdInAndTenantIdAndKnowledgeBaseIdAndStatus(
            List<UUID> ids, UUID tenantId, UUID knowledgeBaseId, DocumentStatus status);

    List<Document> findByStatusOrderByIdAsc(DocumentStatus status, Pageable pageable);

    List<Document> findByStatusAndIdGreaterThanOrderByIdAsc(
            DocumentStatus status, UUID afterId, Pageable pageable);
}
