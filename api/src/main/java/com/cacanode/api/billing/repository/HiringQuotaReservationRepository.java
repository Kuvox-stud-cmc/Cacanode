package com.cacanode.api.billing.repository;

import com.cacanode.api.billing.model.HiringQuotaKind;
import com.cacanode.api.billing.model.HiringQuotaReservation;
import com.cacanode.api.billing.model.HiringQuotaReservationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HiringQuotaReservationRepository extends JpaRepository<HiringQuotaReservation, UUID> {
    Optional<HiringQuotaReservation> findByTenantIdAndQuotaKindAndAggregateId(
            UUID tenantId, HiringQuotaKind quotaKind, UUID aggregateId);

    Optional<HiringQuotaReservation> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            select coalesce(sum(case when r.state = com.cacanode.api.billing.model.HiringQuotaReservationState.COMMITTED
                then r.settledAmount else r.reservedAmount end), 0)
            from HiringQuotaReservation r
            where r.tenantId = :tenantId and r.quotaKind = :kind and r.state in :states
              and (r.expiresAt is null or r.expiresAt > :now or r.state = com.cacanode.api.billing.model.HiringQuotaReservationState.COMMITTED)
            """)
    long sumCounted(@Param("tenantId") UUID tenantId, @Param("kind") HiringQuotaKind kind,
                    @Param("states") Collection<HiringQuotaReservationState> states,
                    @Param("now") LocalDateTime now);

    List<HiringQuotaReservation> findTop200ByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            HiringQuotaReservationState state, LocalDateTime expiresAt);

    @Modifying
    @Query("""
            update HiringQuotaReservation r set r.state = :expired, r.terminalAt = :now
            where r.tenantId = :tenantId and r.state = :reserved
              and r.expiresAt is not null and r.expiresAt <= :now
            """)
    int expireStale(@Param("tenantId") UUID tenantId,
                    @Param("reserved") HiringQuotaReservationState reserved,
                    @Param("expired") HiringQuotaReservationState expired,
                    @Param("now") LocalDateTime now);
}
