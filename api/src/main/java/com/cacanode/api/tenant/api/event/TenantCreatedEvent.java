package com.cacanode.api.tenant.api.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantCreatedEvent(
        UUID tenantId,
        UUID adminUserId,
        LocalDateTime trialStartsAt,
        LocalDateTime trialEndsAt,
        String name,
        String status,
        String plan,
        long maxStorageMb,
        LocalDateTime createdAt
) {
    public TenantCreatedEvent(UUID tenantId, UUID adminUserId,
                              LocalDateTime trialStartsAt, LocalDateTime trialEndsAt) {
        this(tenantId, adminUserId, trialStartsAt, trialEndsAt,
                "", "TRIAL", "TRIAL", 0, trialStartsAt);
    }
}
