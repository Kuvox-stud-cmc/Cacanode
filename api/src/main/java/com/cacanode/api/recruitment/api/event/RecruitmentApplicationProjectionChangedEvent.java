package com.cacanode.api.recruitment.api.event;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record RecruitmentApplicationProjectionChangedEvent(
        UUID tenantId, UUID applicationId, UUID jobId, String status, String businessEvent,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime submittedAt,
        LocalDateTime verifiedAt, LocalDateTime withdrawnAt) {

    public Map<String, Object> webhookPayload() {
        return PayloadSupport.map(
                "tenantId", tenantId, "applicationId", applicationId, "jobId", jobId,
                "status", status, "occurredAt", updatedAt, "submittedAt", submittedAt,
                "verifiedAt", verifiedAt, "withdrawnAt", withdrawnAt);
    }
}
