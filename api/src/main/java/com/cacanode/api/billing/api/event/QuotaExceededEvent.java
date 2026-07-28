package com.cacanode.api.billing.api.event;

import java.util.UUID;

public record QuotaExceededEvent(UUID tenantId, long used, int limit) {
}
