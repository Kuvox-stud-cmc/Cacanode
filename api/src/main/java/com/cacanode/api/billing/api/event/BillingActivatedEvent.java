package com.cacanode.api.billing.api.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record BillingActivatedEvent(
        UUID tenantId,
        UUID userId,
        UUID paymentId,
        String interval,
        LocalDateTime paidThroughAt,
        String planCode
) {
    public BillingActivatedEvent(UUID tenantId, UUID userId, UUID paymentId,
                                 String interval, LocalDateTime paidThroughAt) {
        this(tenantId, userId, paymentId, interval, paidThroughAt, null);
    }

    public String effectivePlanCode() {
        return planCode == null || planCode.isBlank() ? "PRO" : planCode;
    }
}
