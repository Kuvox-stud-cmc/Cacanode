package com.cacanode.api.tenant.api.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProjectionChangedEvent(
        UUID userId, UUID tenantId, String status, String role,
        LocalDateTime createdAt, LocalDateTime updatedAt) {}
