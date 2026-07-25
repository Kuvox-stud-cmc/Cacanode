package com.cacanode.api.recruitment.api.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecruitmentErasureCompletedEvent(UUID tenantId,UUID applicationId,
        List<UUID> interviewIds,String status,Instant occurredAt) {}
