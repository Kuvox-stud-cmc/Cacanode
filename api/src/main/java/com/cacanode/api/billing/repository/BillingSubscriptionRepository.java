package com.cacanode.api.billing.repository;

import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.billing.model.BillingSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingSubscriptionRepository extends JpaRepository<BillingSubscription, UUID> {
    Optional<BillingSubscription> findByTenantId(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from BillingSubscription s where s.tenantId = :tenantId")
    Optional<BillingSubscription> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);

    List<BillingSubscription> findByStatusIn(Collection<BillingStatus> statuses);
}
