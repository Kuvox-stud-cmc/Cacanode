package com.cacanode.api.tenant.api.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantProjectionChangedEvent(
        UUID tenantId, String name, String status, String plan, long maxStorageMb,
        LocalDateTime createdAt, LocalDateTime updatedAt) {}
