package com.cacanode.api.document.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentType;
import com.cacanode.api.document.enums.DocumentVisibility;

public record DocumentListItemResponse(
        UUID id,
        UUID jobId,
        String fileName,
        DocumentType fileType,
        long fileSizeBytes,
        UUID knowledgeBaseId,
        DocumentStatus status,
        DocumentVisibility visibility,
        Integer chunkCount,
        String errorMessage,
        LocalDateTime uploadedAt
) {
}
