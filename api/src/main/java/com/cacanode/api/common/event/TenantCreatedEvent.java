package com.cacanode.api.common.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantCreatedEvent(
        UUID tenantId,
        UUID adminUserId,
        LocalDateTime trialStartsAt,
        LocalDateTime trialEndsAt
) {
}
