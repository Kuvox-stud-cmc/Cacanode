package com.cacanode.api.billing.model;

import com.cacanode.api.common.model.BaseImmutableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "billing_webhook_events")
public class BillingWebhookEvent extends BaseImmutableEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    private BillingPaymentOrder paymentOrder;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "payload_hash", nullable = false, unique = true, length = 64)
    private String payloadHash;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "processing_result", nullable = false, length = 50)
    private String processingResult;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
