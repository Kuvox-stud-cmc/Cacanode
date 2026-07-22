package com.cacanode.api.billing.gateway;

import com.cacanode.api.billing.api.PaymentOrderStatus;

import java.time.LocalDateTime;
import java.util.Map;

public interface PaymentGateway {
    CreatedPayment createPayment(CreatePayment request);

    VerifiedWebhook verifyWebhook(Map<String, Object> payload);

    ProviderPayment getPayment(long orderCode);

    record CreatePayment(
            long orderCode,
            long amountVnd,
            String description,
            String itemName,
            String returnUrl,
            String cancelUrl,
            LocalDateTime expiresAt
    ) {
    }

    record CreatedPayment(String paymentLinkId, String checkoutUrl, LocalDateTime expiresAt) {
    }

    record VerifiedWebhook(
            long orderCode,
            long amount,
            String currency,
            String paymentLinkId,
            String providerReference,
            boolean successful
    ) {
    }

    record ProviderPayment(
            long orderCode,
            String paymentLinkId,
            long amount,
            long amountPaid,
            PaymentOrderStatus status,
            String providerReference
    ) {
    }
}
