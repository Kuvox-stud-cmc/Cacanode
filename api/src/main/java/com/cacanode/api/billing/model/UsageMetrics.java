package com.cacanode.api.billing.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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
                @UniqueConstraint(
                        name = "uq_usage_metric_tenant_period",
                        columnNames = {"tenant_id", "period_year", "period_month"}
                )
        }
)
public class UsageMetrics extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantID;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "message_count", nullable = false)
    private int messageCount = 0;

    @Column(name = "document_count", nullable = false)
    private int documentCount = 0;

    @Column(name = "storage_mb_used", nullable = false, precision = 10, scale = 2)
    private BigDecimal storageMbUsed = BigDecimal.ZERO;

    @Column(name = "token_count", nullable = false)
    private Long tokenCount = 0L;

}
