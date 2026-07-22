package com.cacanode.api.document.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.document.api.DocumentApi;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentVisibility;
import com.cacanode.api.document.model.Document;
import com.cacanode.api.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentApiImpl implements DocumentApi {
    private final DocumentRepository repository;
    private final PublicEvidenceService publicEvidenceService;

    @Override
    @Transactional(readOnly = true)
    public List<UUID> visibleDocumentIds(UUID tenantId, UUID knowledgeBaseId, boolean customerVisibleOnly) {
        if (customerVisibleOnly) {
            return repository.findByTenantIdAndKnowledgeBaseIdAndStatusAndVisibility(
                            tenantId, knowledgeBaseId, DocumentStatus.COMPLETED,
                            DocumentVisibility.CUSTOMER_AND_EMPLOYEE).stream()
                    .map(Document::getId).sorted().toList();
        }
        return repository.findByTenantIdAndKnowledgeBaseIdAndStatus(
                        tenantId, knowledgeBaseId, DocumentStatus.COMPLETED).stream()
                .map(Document::getId).sorted().toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void validateCitations(
            UUID tenantId, UUID knowledgeBaseId, List<UUID> documentIds, boolean customerVisibleOnly) {
        if (documentIds.isEmpty()) {
            return;
        }
        List<Document> documents = repository.findByIdInAndTenantIdAndKnowledgeBaseIdAndStatus(
                documentIds, tenantId, knowledgeBaseId, DocumentStatus.COMPLETED);
        boolean invalid = documents.size() != documentIds.size()
                || customerVisibleOnly && documents.stream().anyMatch(
                document -> document.getVisibility() != DocumentVisibility.CUSTOMER_AND_EMPLOYEE);
        if (invalid) {
            throw new BadRequestException("The model returned an invalid citation.");
        }
    }

    @Override
    public String issuePublicEvidenceUrl(
            UUID tenantId, UUID knowledgeBaseId, UUID integrationTokenId, EvidenceCitation citation) {
        return publicEvidenceService.issue(tenantId, knowledgeBaseId, integrationTokenId, citation);
    }

    @Override
    @Transactional(readOnly = true)
    public UsageSnapshot usage(UUID tenantId) {
        return new UsageSnapshot(repository.countByTenantIdAndStatusNot(tenantId, DocumentStatus.FAILED),
                repository.sumFileSizeByTenantIdAndStatusNot(tenantId, DocumentStatus.FAILED));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectionPage projectionSnapshots(int page, int size) {
        var result = repository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new ProjectionPage(result.stream().map(document -> new ProjectionSnapshot(
                document.getId(), document.getTenantId(), document.getFileName(),
                document.getFileType().name(), document.getStatus().name(),
                document.getVisibility().name(), document.getFileSizeBytes(),
                document.getCreatedAt(), document.getUpdatedAt())).toList(), result.hasNext());
    }
}
