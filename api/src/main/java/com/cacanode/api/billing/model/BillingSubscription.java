package com.cacanode.api.billing.model;

import com.cacanode.api.billing.api.BillingInterval;
import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "billing_subscriptions", uniqueConstraints =
        @UniqueConstraint(name = "uq_billing_subscription_tenant", columnNames = "tenant_id"))
public class BillingSubscription extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, length = 30)
    private BillingPlanCode planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", length = 20)
    private BillingInterval billingInterval;

    @Column(name = "catalog_version", nullable = false, length = 50)
    private String catalogVersion;

    @Column(name = "quota_anchor_at", nullable = false)
    private LocalDateTime quotaAnchorAt;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Column(name = "paid_through_at")
    private LocalDateTime paidThroughAt;

    @Column(name = "grace_ends_at")
    private LocalDateTime graceEndsAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "reminder_7_sent_at")
    private LocalDateTime reminder7SentAt;

    @Column(name = "reminder_3_sent_at")
    private LocalDateTime reminder3SentAt;

    @Column(name = "reminder_1_sent_at")
    private LocalDateTime reminder1SentAt;

    @Column(name = "last_grace_reminder_at")
    private LocalDateTime lastGraceReminderAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entitlement_snapshot", nullable = false, columnDefinition = "jsonb")
    private EntitlementSnapshot entitlementSnapshot;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
