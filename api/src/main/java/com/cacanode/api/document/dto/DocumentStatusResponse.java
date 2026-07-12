package com.cacanode.api.document.dto;

import java.util.UUID;

import com.cacanode.api.document.enums.DocumentStatus;

public record DocumentStatusResponse(
        UUID id,
        UUID jobId,
        String fileName,
        UUID knowledgeBaseId,
        DocumentStatus status,
        Integer chunkCount,
        String errorMessage
) {
}
