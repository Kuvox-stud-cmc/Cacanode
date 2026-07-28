package com.cacanode.api.recruitment.api.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record RecruitmentInterviewProjectionChangedEvent(
        UUID tenantId, UUID interviewId, UUID applicationId, UUID jobId,
        String status, String businessEvent, LocalDateTime createdAt, LocalDateTime updatedAt,
        LocalDateTime invitedAt, Instant scheduledStartAt, Instant scheduledEndAt,
        String schedulingTimezone, int rescheduleCount, LocalDateTime startedAt,
        LocalDateTime completedAt, LocalDateTime cancelledAt, LocalDateTime expiredAt) {

    public Map<String, Object> webhookPayload() {
        return PayloadSupport.map(
                "tenantId", tenantId, "interviewId", interviewId, "applicationId", applicationId,
                "jobId", jobId, "status", status, "occurredAt", updatedAt,
                "scheduledStartAt", scheduledStartAt, "scheduledEndAt", scheduledEndAt,
                "schedulingTimezone", schedulingTimezone, "rescheduleCount", rescheduleCount,
                "startedAt", startedAt, "completedAt", completedAt,
                "cancelledAt", cancelledAt, "expiredAt", expiredAt);
    }
}
