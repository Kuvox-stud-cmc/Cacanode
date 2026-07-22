package com.cacanode.api.document.api;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

public interface DocumentApi {
    List<UUID> visibleDocumentIds(UUID tenantId, UUID knowledgeBaseId, boolean customerVisibleOnly);

    void validateCitations(
            UUID tenantId, UUID knowledgeBaseId, List<UUID> documentIds, boolean customerVisibleOnly);

    String issuePublicEvidenceUrl(
            UUID tenantId, UUID knowledgeBaseId, UUID integrationTokenId, EvidenceCitation citation);

    UsageSnapshot usage(UUID tenantId);

    ProjectionPage projectionSnapshots(int page, int size);

    record EvidenceCitation(
            String documentId,
            String unitId,
            int chunkIndex,
            Integer pageNumber,
            String sheetName,
            String cellRange,
            String tableId
    ) {
    }

    record UsageSnapshot(long documentCount, long storageBytes) {
    }

    record ProjectionPage(List<ProjectionSnapshot> items, boolean hasMore) {
        public ProjectionPage { items = List.copyOf(items); }
    }

    record ProjectionSnapshot(UUID id, UUID tenantId, String fileName, String fileType,
                              String status, String visibility, long fileSizeBytes,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
