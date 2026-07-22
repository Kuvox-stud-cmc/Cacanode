package com.cacanode.api.tenant.api.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record InvitationProjectionChangedEvent(
        UUID invitationId, UUID tenantId, String status, LocalDateTime createdAt,
        LocalDateTime expiresAt, LocalDateTime updatedAt) {}
