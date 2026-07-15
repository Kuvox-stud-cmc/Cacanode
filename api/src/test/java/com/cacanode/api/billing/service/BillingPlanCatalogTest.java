package com.cacanode.api.billing.service;

import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.enums.BillingInterval;
import com.cacanode.api.billing.enums.BillingPlanCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BillingPlanCatalogTest {
    private final BillingProperties properties = new BillingProperties();
    private final BillingPlanCatalog catalog = new BillingPlanCatalog(properties);

    @Test
    void resolvesPricesAndEntitlementsOnlyOnTheServer() {
        assertEquals(1_199_000L, catalog.price(BillingPlanCode.PRO, BillingInterval.MONTHLY));
        assertEquals(11_990_000L, catalog.price(BillingPlanCode.PRO, BillingInterval.ANNUAL));
        assertEquals(500, catalog.entitlements(BillingPlanCode.STARTER).maxMessages());
        assertEquals(10_000, catalog.entitlements(BillingPlanCode.TRIAL).maxMessages());
        assertNull(catalog.entitlements(BillingPlanCode.ENTERPRISE).maxMessages());
        assertThrows(IllegalArgumentException.class,
                () -> catalog.price(BillingPlanCode.STARTER, BillingInterval.MONTHLY));
    }

    @Test
    void publicCatalogContainsNoTrialCheckoutAndMarksEnterpriseAsSalesLed() {
        var plans = catalog.publicPlans();
        assertEquals(3, plans.size());
        assertTrue(plans.stream().noneMatch(plan -> plan.planCode() == BillingPlanCode.TRIAL));
        assertTrue(plans.stream().filter(plan -> plan.planCode() == BillingPlanCode.ENTERPRISE)
                .findFirst().orElseThrow().contactSales());
    }
}
