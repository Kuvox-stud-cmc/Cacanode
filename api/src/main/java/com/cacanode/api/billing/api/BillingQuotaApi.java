package com.cacanode.api.billing.api;

import java.util.UUID;

public interface BillingQuotaApi {
    QuotaConsumption consumeMessageQuota(UUID tenantId);

    void rollbackMessageQuota(UUID tenantId, UUID consumptionId);

    record QuotaConsumption(UUID consumptionId, long used, Integer limit) {
    }
}
