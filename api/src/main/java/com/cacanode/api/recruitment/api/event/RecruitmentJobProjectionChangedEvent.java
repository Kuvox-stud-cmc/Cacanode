package com.cacanode.api.recruitment.api.event;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record RecruitmentJobProjectionChangedEvent(
        UUID tenantId, UUID jobId, String status, String businessEvent,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime publishedAt,
        LocalDateTime pausedAt, LocalDateTime closedAt, LocalDateTime archivedAt) {

    public Map<String, Object> webhookPayload() {
        return PayloadSupport.map(
                "tenantId", tenantId, "jobId", jobId, "status", status,
                "occurredAt", updatedAt, "publishedAt", publishedAt,
                "pausedAt", pausedAt, "closedAt", closedAt, "archivedAt", archivedAt);
    }
}
