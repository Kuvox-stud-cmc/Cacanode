package com.cacanode.api.billing.api;

import com.cacanode.api.billing.api.BillingInterval;
import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.billing.api.PaymentOrderStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {
    }

    public record Limits(
            Integer messages,
            Integer documents,
            Integer teamMembers,
            Integer storageMb,
            Long activeJobs,
            Long verifiedApplications,
            Long interviewSeconds,
            Long cvAnalyses,
            Long recruitmentStorageBytes
    ) {
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

    public record CheckoutResponse(
            UUID paymentId,
            BillingPlanCode planCode,
            BillingInterval interval,
            long amountVnd,
            String checkoutUrl,
            LocalDateTime expiresAt
    ) {
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

    public record HiringUsageItem(long used, long reserved, long limit, boolean overLimit) {
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
            HiringUsageItem activeJobs,
            HiringUsageItem verifiedApplications,
            HiringUsageItem interviewSeconds,
            HiringUsageItem cvAnalyses,
            HiringUsageItem recruitmentStorageBytes,
            Features features,
            PaymentResponse pendingPayment,
            boolean cancelAtPeriodEnd
    ) {
    }

    public record DowngradeResponse(boolean scheduled, LocalDateTime effectiveAt, AccountResponse account) {
    }
}
