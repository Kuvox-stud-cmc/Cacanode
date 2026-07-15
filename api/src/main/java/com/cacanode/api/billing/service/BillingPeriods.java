package com.cacanode.api.billing.service;

import com.cacanode.api.billing.enums.BillingPlanCode;
import com.cacanode.api.billing.enums.BillingStatus;
import com.cacanode.api.billing.model.BillingSubscription;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class BillingPeriods {
    public Period currentQuotaPeriod(BillingSubscription subscription, LocalDateTime now) {
        LocalDateTime anchor = subscription.getQuotaAnchorAt();
        if (subscription.getPlanCode() == BillingPlanCode.TRIAL) {
            return new Period(anchor, subscription.getTrialEndsAt());
        }
        if (subscription.getPlanCode() == BillingPlanCode.STARTER) {
            return anniversaryMonth(anchor, now, null);
        }
        if (subscription.getPlanCode() == BillingPlanCode.ENTERPRISE) {
            return anniversaryMonth(anchor, now, null);
        }

        LocalDateTime effectiveNow = now;
        if (subscription.getStatus() == BillingStatus.GRACE && subscription.getPaidThroughAt() != null) {
            effectiveNow = subscription.getPaidThroughAt().minusNanos(1);
        }
        return anniversaryMonth(anchor, effectiveNow, subscription.getPaidThroughAt());
    }

    private Period anniversaryMonth(LocalDateTime anchor, LocalDateTime now, LocalDateTime cap) {
        LocalDateTime start = anchor;
        LocalDateTime end = start.plusMonths(1);
        while (!now.isBefore(end)) {
            start = end;
            end = end.plusMonths(1);
        }
        if (cap != null && end.isAfter(cap)) {
            end = cap;
        }
        return new Period(start, end);
    }

    public record Period(LocalDateTime start, LocalDateTime end) {
    }
}
