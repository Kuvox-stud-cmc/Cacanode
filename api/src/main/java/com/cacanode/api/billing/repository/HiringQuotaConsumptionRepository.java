package com.cacanode.api.billing.repository;

import com.cacanode.api.billing.model.HiringQuotaConsumption;
import com.cacanode.api.billing.model.HiringQuotaKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HiringQuotaConsumptionRepository extends JpaRepository<HiringQuotaConsumption, UUID> {
    Optional<HiringQuotaConsumption> findByTenantIdAndQuotaKindAndAggregateId(
            UUID tenantId, HiringQuotaKind quotaKind, UUID aggregateId);
}
