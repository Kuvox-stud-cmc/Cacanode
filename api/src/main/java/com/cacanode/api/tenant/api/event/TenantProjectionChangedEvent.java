package com.cacanode.api.tenant.api.event;

import java.time.LocalDateTime;
import java.util.UUID;
import com.cacanode.api.tenant.api.TenantKind;

public record TenantProjectionChangedEvent(
        UUID tenantId, String name, String status, String plan, long maxStorageMb,
        LocalDateTime createdAt, LocalDateTime updatedAt, TenantKind kind) {
    public TenantProjectionChangedEvent {
        kind = TenantKind.defaulted(kind);
    }

    public TenantProjectionChangedEvent(UUID tenantId, String name, String status, String plan,
                                        long maxStorageMb, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(tenantId, name, status, plan, maxStorageMb, createdAt, updatedAt, TenantKind.CUSTOMER);
    }
}
