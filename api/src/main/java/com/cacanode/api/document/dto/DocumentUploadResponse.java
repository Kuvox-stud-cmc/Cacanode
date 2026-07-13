package com.cacanode.api.document.dto;

import java.util.UUID;

import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentVisibility;

public record DocumentUploadResponse(
        UUID id,
        UUID jobId,
        String fileName,
        DocumentStatus status,
        DocumentVisibility visibility
) {
}
