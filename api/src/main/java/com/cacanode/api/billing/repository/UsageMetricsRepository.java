package com.cacanode.api.billing.repository;

import com.cacanode.api.billing.model.UsageMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Optional;

public interface UsageMetricsRepository extends JpaRepository<UsageMetrics, UUID> {
    Optional<UsageMetrics> findByTenantIDAndPeriodStart(UUID tenantId, LocalDateTime periodStart);
}
