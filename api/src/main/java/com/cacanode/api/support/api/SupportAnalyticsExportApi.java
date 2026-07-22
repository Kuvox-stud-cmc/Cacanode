package com.cacanode.api.support.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SupportAnalyticsExportApi {
    TicketPage projectionTickets(int page, int size);

    record TicketPage(List<TicketSnapshot> items, boolean hasMore) {
        public TicketPage { items = List.copyOf(items); }
    }
    record TicketSnapshot(UUID id, UUID tenantId, String status, String priority,
                          LocalDateTime createdAt, LocalDateTime resolvedAt,
                          LocalDateTime updatedAt) {}
}
