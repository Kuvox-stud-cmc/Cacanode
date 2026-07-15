package com.cacanode.api.document.repository;

import com.cacanode.api.document.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.cacanode.api.document.enums.DocumentStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {
    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Document> findByTenantIdAndKnowledgeBaseIdOrderByCreatedAtDescIdDesc(
            UUID tenantId,
            UUID knowledgeBaseId
    );

    long countByTenantIdAndStatusNot(UUID tenantId, DocumentStatus status);

    @Query("select coalesce(sum(d.fileSizeBytes), 0) from Document d where d.tenantId = :tenantId and d.status <> :status")
    long sumFileSizeByTenantIdAndStatusNot(@Param("tenantId") UUID tenantId, @Param("status") DocumentStatus status);
}
