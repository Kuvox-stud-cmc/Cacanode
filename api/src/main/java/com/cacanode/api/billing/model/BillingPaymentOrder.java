package com.cacanode.api.billing.model;

import com.cacanode.api.billing.api.BillingInterval;
import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.PaymentOrderStatus;
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
@Table(name = "billing_payment_orders")
public class BillingPaymentOrder extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_code", nullable = false, unique = true)
    private long orderCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_plan", nullable = false, length = 30)
    private BillingPlanCode requestedPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 20)
    private BillingInterval billingInterval;

    @Column(name = "amount_vnd", nullable = false)
    private long amountVnd;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "catalog_version", nullable = false, length = 50)
    private String catalogVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entitlement_snapshot", nullable = false, columnDefinition = "jsonb")
    private EntitlementSnapshot entitlementSnapshot;

    @Column(name = "payment_link_id")
    private String paymentLinkId;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentOrderStatus status;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "client_idempotency_key")
    private String clientIdempotencyKey;
}
