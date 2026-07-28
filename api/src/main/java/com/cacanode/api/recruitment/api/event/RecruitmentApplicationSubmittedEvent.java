package com.cacanode.api.recruitment.api.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecruitmentApplicationSubmittedEvent(UUID eventId,UUID applicationId,UUID jobId,
        UUID tenantId,LocalDateTime occurredAt) {}
