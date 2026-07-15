package com.cacanode.api.billing.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "usage_metrics",
        indexes = {
                @Index(name = "idx_usage_metric_tenant_id", columnList = "tenant_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_usage_metric_tenant_period_start", columnNames = {"tenant_id", "period_start"})
        }
)
public class UsageMetrics extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantID;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "warning_80_sent", nullable = false)
    private boolean warning80Sent;

    @Column(name = "exceeded_sent", nullable = false)
    private boolean exceededSent;

    @Column(name = "message_count", nullable = false)
    private int messageCount = 0;

    @Column(name = "document_count", nullable = false)
    private int documentCount = 0;

    @Column(name = "storage_mb_used", nullable = false, precision = 10, scale = 2)
    private BigDecimal storageMbUsed = BigDecimal.ZERO;

    @Column(name = "token_count", nullable = false)
    private Long tokenCount = 0L;

}
