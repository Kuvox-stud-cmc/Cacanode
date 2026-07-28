package com.cacanode.api.support.api.event;

import java.util.UUID;
import java.time.LocalDateTime;

public record TicketStatusChangedEvent(
        UUID tenantId, UUID ticketId, String status,
        LocalDateTime resolvedAt, LocalDateTime updatedAt) {
    public TicketStatusChangedEvent(UUID tenantId, UUID ticketId, String status) {
        this(tenantId, ticketId, status, null, LocalDateTime.now());
    }
}
