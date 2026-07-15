package com.cacanode.api.billing.api;

import com.cacanode.api.billing.dto.BillingDtos;
import com.cacanode.api.billing.service.BillingService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface BillingModuleApi extends BillingService {
    List<BillingDtos.PublicPlan> plans();

    BillingDtos.AccountResponse account(UUID tenantId);

    BillingDtos.CheckoutResponse createCheckout(
            UUID tenantId, UUID userId, BillingDtos.CheckoutRequest request, String idempotencyKey);

    BillingDtos.PaymentResponse payment(UUID tenantId, UUID paymentId);

    BillingDtos.DowngradeResponse downgrade(UUID tenantId);

    void processPayOsWebhook(Map<String, Object> payload);
}
