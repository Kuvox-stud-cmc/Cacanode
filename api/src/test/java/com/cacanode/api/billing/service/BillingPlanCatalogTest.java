package com.cacanode.api.billing.service;

import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.api.BillingInterval;
import com.cacanode.api.billing.api.BillingPlanCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BillingPlanCatalogTest {
    private final BillingProperties properties = new BillingProperties();
    private final BillingPlanCatalog catalog = new BillingPlanCatalog(properties);

    @Test
    void resolvesPricesAndEntitlementsOnlyOnTheServer() {
        assertEquals(1_199_000L, catalog.price(BillingPlanCode.PRO, BillingInterval.MONTHLY));
        assertEquals(11_990_000L, catalog.price(BillingPlanCode.PRO, BillingInterval.ANNUAL));
        assertEquals(3_499_000L, catalog.price(BillingPlanCode.BUSINESS, BillingInterval.MONTHLY));
        assertEquals(34_990_000L, catalog.price(BillingPlanCode.BUSINESS, BillingInterval.ANNUAL));
        assertEquals(500, catalog.entitlements(BillingPlanCode.STARTER).maxMessages());
        assertEquals(10_000, catalog.entitlements(BillingPlanCode.TRIAL).maxMessages());
        assertNull(catalog.entitlements(BillingPlanCode.ENTERPRISE).maxMessages());
        assertThrows(IllegalArgumentException.class,
                () -> catalog.price(BillingPlanCode.STARTER, BillingInterval.MONTHLY));
    }

    @Test
    void publicCatalogContainsNoTrialCheckoutAndMarksEnterpriseAsSalesLed() {
        var plans = catalog.publicPlans();
        assertEquals(4, plans.size());
        assertEquals(java.util.List.of(
                        BillingPlanCode.STARTER, BillingPlanCode.PRO,
                        BillingPlanCode.BUSINESS, BillingPlanCode.ENTERPRISE),
                plans.stream().map(plan -> plan.planCode()).toList());
        assertTrue(plans.stream().noneMatch(plan -> plan.planCode() == BillingPlanCode.TRIAL));
        assertTrue(plans.stream().filter(plan -> plan.planCode() == BillingPlanCode.ENTERPRISE)
                .findFirst().orElseThrow().contactSales());
        assertTrue(plans.stream().filter(plan -> plan.planCode() == BillingPlanCode.PRO)
                .findFirst().orElseThrow().highlighted());
        var business = plans.stream().filter(plan -> plan.planCode() == BillingPlanCode.BUSINESS)
                .findFirst().orElseThrow();
        assertEquals(10L, business.limits().activeJobs());
        assertEquals(1_000L, business.limits().verifiedApplications());
        assertEquals(18_000L, business.limits().interviewSeconds());
        assertEquals(500L, business.limits().cvAnalyses());
        assertEquals(10_737_418_240L, business.limits().recruitmentStorageBytes());
        assertFalse(business.contactSales());
    }
}
