package com.cacanode.api.billing.repository;

import com.cacanode.api.billing.model.UsageMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsageMetricsRepository extends JpaRepository<UsageMetrics, UUID> {
}
