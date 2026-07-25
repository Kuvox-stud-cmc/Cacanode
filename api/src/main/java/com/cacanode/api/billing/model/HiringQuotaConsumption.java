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
@Table(name = "hiring_quota_consumptions", uniqueConstraints =
        @UniqueConstraint(name = "uq_hiring_consumption_semantic", columnNames = {"tenant_id", "quota_kind", "aggregate_id"}))
public class HiringQuotaConsumption extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "quota_kind", nullable = false, length = 40)
    private HiringQuotaKind quotaKind;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "consumed_amount", nullable = false)
    private long consumedAmount;

    @Column(name = "remaining_amount", nullable = false)
    private long remainingAmount;
}
