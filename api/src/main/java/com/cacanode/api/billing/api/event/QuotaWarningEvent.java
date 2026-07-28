package com.cacanode.api.billing.api.event;

import java.util.UUID;

public record QuotaWarningEvent(UUID tenantId, long used, int limit) {
}
