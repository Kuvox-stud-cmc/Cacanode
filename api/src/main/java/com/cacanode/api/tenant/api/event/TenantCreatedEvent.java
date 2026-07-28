package com.cacanode.api.tenant.api.event;

import java.time.LocalDateTime;
import java.util.UUID;
import com.cacanode.api.tenant.api.TenantKind;

public record TenantCreatedEvent(
        UUID tenantId,
        UUID adminUserId,
        LocalDateTime trialStartsAt,
        LocalDateTime trialEndsAt,
        String name,
        String status,
        String plan,
        long maxStorageMb,
        LocalDateTime createdAt,
        TenantKind kind
) {
    public TenantCreatedEvent {
        kind = TenantKind.defaulted(kind);
    }

    public TenantCreatedEvent(UUID tenantId, UUID adminUserId, LocalDateTime trialStartsAt,
                              LocalDateTime trialEndsAt, String name, String status, String plan,
                              long maxStorageMb, LocalDateTime createdAt) {
        this(tenantId, adminUserId, trialStartsAt, trialEndsAt, name, status, plan,
                maxStorageMb, createdAt, TenantKind.CUSTOMER);
    }
    public TenantCreatedEvent(UUID tenantId, UUID adminUserId,
                              LocalDateTime trialStartsAt, LocalDateTime trialEndsAt) {
        this(tenantId, adminUserId, trialStartsAt, trialEndsAt,
                "", "TRIAL", "TRIAL", 0, trialStartsAt, TenantKind.CUSTOMER);
    }
}
