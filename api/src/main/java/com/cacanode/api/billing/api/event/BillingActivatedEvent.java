package com.cacanode.api.billing.api.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record BillingActivatedEvent(
        UUID tenantId,
        UUID userId,
        UUID paymentId,
        String interval,
        LocalDateTime paidThroughAt
) {
}
