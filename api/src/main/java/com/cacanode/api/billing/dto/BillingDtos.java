package com.cacanode.api.billing.dto;

import com.cacanode.api.billing.enums.BillingInterval;
import com.cacanode.api.billing.enums.BillingPlanCode;
import com.cacanode.api.billing.enums.BillingStatus;
import com.cacanode.api.billing.enums.PaymentOrderStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {
    }

    public record Limits(Integer messages, Integer documents, Integer teamMembers, Integer storageMb) {
    }

    public record Features(
            boolean apiAccess,
            boolean webhooks,
            boolean advancedAnalytics,
            boolean customBranding
    ) {
    }

    public record PriceOption(BillingInterval interval, Long amountVnd, String currency, String label) {
    }

    public record PublicPlan(
            BillingPlanCode planCode,
            String name,
            String description,
            Limits limits,
            Features features,
            List<String> includedFeatures,
            List<PriceOption> prices,
            boolean contactSales,
            String salesUrl,
            boolean highlighted
    ) {
    }

    public record CheckoutRequest(@NotNull BillingPlanCode planCode, @NotNull BillingInterval interval) {
    }

    public record CheckoutResponse(UUID paymentId, String checkoutUrl, LocalDateTime expiresAt) {
    }

    public record PaymentResponse(
            UUID paymentId,
            PaymentOrderStatus status,
            BillingPlanCode planCode,
            BillingInterval interval,
            long amountVnd,
            String currency,
            String checkoutUrl,
            LocalDateTime expiresAt,
            LocalDateTime paidAt,
            String failureReason
    ) {
    }

    public record UsageItem(long used, Integer limit, boolean overLimit) {
    }

    public record AccountResponse(
            BillingPlanCode planCode,
            BillingStatus status,
            BillingInterval interval,
            LocalDateTime trialEndsAt,
            LocalDateTime paidThroughAt,
            LocalDateTime graceEndsAt,
            LocalDateTime quotaPeriodStart,
            LocalDateTime nextQuotaResetAt,
            UsageItem messages,
            UsageItem documents,
            UsageItem teamMembers,
            UsageItem storageMb,
            Features features,
            PaymentResponse pendingPayment,
            boolean cancelAtPeriodEnd
    ) {
    }

    public record DowngradeResponse(boolean scheduled, LocalDateTime effectiveAt, AccountResponse account) {
    }
}
