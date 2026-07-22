package com.cacanode.api.document.api.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentProjectionEvent(
        UUID documentId,
        UUID tenantId,
        String fileName,
        String fileType,
        String status,
        String visibility,
        long fileSizeBytes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
