package com.cacanode.api.billing.service;

import com.cacanode.api.billing.api.BillingInterval;
import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.billing.model.BillingSubscription;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingPeriodsTest {
    private final BillingPeriods periods = new BillingPeriods();

    @Test
    void annualSubscriptionStillUsesMonthlyAnniversaryWindows() {
        BillingSubscription subscription = proSubscription();
        subscription.setBillingInterval(BillingInterval.ANNUAL);
        subscription.setPaidThroughAt(LocalDateTime.of(2027, 1, 31, 10, 15));

        var period = periods.currentQuotaPeriod(subscription, LocalDateTime.of(2026, 3, 15, 12, 0));

        assertEquals(LocalDateTime.of(2026, 2, 28, 10, 15), period.start());
        assertEquals(LocalDateTime.of(2026, 3, 28, 10, 15), period.end());
    }

    @Test
    void graceRetainsTheFinalPaidQuotaWindow() {
        BillingSubscription subscription = proSubscription();
        subscription.setStatus(BillingStatus.GRACE);
        subscription.setPaidThroughAt(LocalDateTime.of(2026, 4, 30, 10, 15));
        subscription.setGraceEndsAt(LocalDateTime.of(2026, 5, 3, 10, 15));

        var period = periods.currentQuotaPeriod(subscription, LocalDateTime.of(2026, 5, 2, 9, 0));

        assertEquals(LocalDateTime.of(2026, 4, 28, 10, 15), period.start());
        assertEquals(LocalDateTime.of(2026, 4, 30, 10, 15), period.end());
    }

    @Test
    void trialUsesOneWindowForTheWholeTrial() {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setPlanCode(BillingPlanCode.TRIAL);
        subscription.setStatus(BillingStatus.TRIAL);
        subscription.setQuotaAnchorAt(LocalDateTime.of(2026, 7, 1, 8, 0));
        subscription.setTrialEndsAt(LocalDateTime.of(2026, 7, 15, 8, 0));

        var period = periods.currentQuotaPeriod(subscription, LocalDateTime.of(2026, 7, 10, 8, 0));

        assertEquals(subscription.getQuotaAnchorAt(), period.start());
        assertEquals(subscription.getTrialEndsAt(), period.end());
    }

    @Test
    void starterBusinessAndEnterpriseUseMonthlyAnniversaryWindows() {
        for (BillingPlanCode plan : java.util.List.of(
                BillingPlanCode.STARTER, BillingPlanCode.BUSINESS, BillingPlanCode.ENTERPRISE)) {
            BillingSubscription subscription = proSubscription();
            subscription.setPlanCode(plan);
            if (plan != BillingPlanCode.BUSINESS) subscription.setPaidThroughAt(null);
            else subscription.setPaidThroughAt(LocalDateTime.of(2027, 1, 31, 10, 15));

            var period = periods.currentQuotaPeriod(subscription, LocalDateTime.of(2026, 3, 15, 12, 0));

            assertEquals(LocalDateTime.of(2026, 2, 28, 10, 15), period.start(), plan.name());
            assertEquals(LocalDateTime.of(2026, 3, 28, 10, 15), period.end(), plan.name());
        }
    }

    private BillingSubscription proSubscription() {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setPlanCode(BillingPlanCode.PRO);
        subscription.setStatus(BillingStatus.ACTIVE);
        subscription.setQuotaAnchorAt(LocalDateTime.of(2026, 1, 31, 10, 15));
        return subscription;
    }
}
