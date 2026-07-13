package com.cacanode.api.document.dto;

import java.util.UUID;

import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentVisibility;

public record DocumentStatusResponse(
        UUID id,
        UUID jobId,
        String fileName,
        UUID knowledgeBaseId,
        DocumentStatus status,
        DocumentVisibility visibility,
        Integer chunkCount,
        String errorMessage
) {
}
