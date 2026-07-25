package com.cacanode.api.billing.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "hiring_quota_reservations", uniqueConstraints =
        @UniqueConstraint(name = "uq_hiring_reservation_semantic", columnNames = {"tenant_id", "quota_kind", "aggregate_id"}))
public class HiringQuotaReservation extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "quota_kind", nullable = false, length = 40)
    private HiringQuotaKind quotaKind;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private HiringQuotaReservationState state;

    @Column(name = "reserved_amount", nullable = false)
    private long reservedAmount;

    @Column(name = "settled_amount")
    private Long settledAmount;

    @Column(name = "settlement_period_start")
    private LocalDateTime settlementPeriodStart;

    @Column(name = "settlement_period_end")
    private LocalDateTime settlementPeriodEnd;

    @Column(name = "remaining_amount")
    private Long remainingAmount;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "terminal_at")
    private LocalDateTime terminalAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
