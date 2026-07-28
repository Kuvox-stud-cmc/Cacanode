package com.cacanode.api.billing.api;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BillingPlatformReadApi {
    Optional<Account> accountIfPresent(UUID tenantId);

    record Quota(long used, long reserved, Long limit, Double utilizationPercentage,
                 boolean unlimited, boolean overLimit) {}
    record Account(String plan, String status, LocalDateTime periodStart, LocalDateTime periodEnd,
                   Map<String, Quota> quotas) {
        public Account { quotas = Map.copyOf(quotas); }
    }
}
