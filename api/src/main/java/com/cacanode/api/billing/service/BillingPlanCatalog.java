package com.cacanode.api.billing.service;

import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.api.BillingDtos;
import com.cacanode.api.billing.api.BillingInterval;
import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.model.EntitlementSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingPlanCatalog {
    private final BillingProperties properties;

    public String version() {
        return properties.getCatalogVersion();
    }

    public EntitlementSnapshot entitlements(BillingPlanCode planCode) {
        return switch (planCode) {
            case STARTER -> properties.starterEntitlements();
            case TRIAL, PRO -> properties.proEntitlements();
            case ENTERPRISE -> properties.enterpriseEntitlements();
        };
    }

    public long price(BillingPlanCode planCode, BillingInterval interval) {
        if (planCode != BillingPlanCode.PRO) {
            throw new IllegalArgumentException("Only Pro supports self-service checkout");
        }
        return interval == BillingInterval.MONTHLY
                ? properties.getProMonthlyPriceVnd() : properties.getProAnnualPriceVnd();
    }

    public List<BillingDtos.PublicPlan> publicPlans() {
        EntitlementSnapshot starter = entitlements(BillingPlanCode.STARTER);
        EntitlementSnapshot pro = entitlements(BillingPlanCode.PRO);
        EntitlementSnapshot enterprise = entitlements(BillingPlanCode.ENTERPRISE);
        return List.of(
                plan(BillingPlanCode.STARTER, "Starter", "For getting started with one workspace.", starter,
                        List.of("Widget", "Dashboard summary", "CacaNode branding"),
                        List.of(new BillingDtos.PriceOption(null, 0L, "VND", "Free")), false, false),
                plan(BillingPlanCode.PRO, "Pro", "For customer support teams running at scale.", pro,
                        List.of("API access", "Webhooks", "Advanced analytics", "Custom branding"),
                        List.of(
                                new BillingDtos.PriceOption(BillingInterval.MONTHLY,
                                        properties.getProMonthlyPriceVnd(), "VND", "per month"),
                                new BillingDtos.PriceOption(BillingInterval.ANNUAL,
                                        properties.getProAnnualPriceVnd(), "VND", "per year")
                        ), false, true),
                plan(BillingPlanCode.ENTERPRISE, "Enterprise", "Custom limits and sales-provisioned features.", enterprise,
                        List.of("Custom limits", "Advanced analytics", "API access", "Webhooks", "Custom branding"),
                        List.of(), true, false)
        );
    }

    private BillingDtos.PublicPlan plan(
            BillingPlanCode code, String name, String description, EntitlementSnapshot e,
            List<String> included, List<BillingDtos.PriceOption> prices, boolean contactSales, boolean highlighted
    ) {
        return new BillingDtos.PublicPlan(
                code, name, description,
                new BillingDtos.Limits(e.maxMessages(), e.maxDocuments(), e.maxTeamMembers(), e.maxStorageMb()),
                new BillingDtos.Features(e.apiAccess(), e.webhooks(), e.advancedAnalytics(), e.customBranding()),
                included, prices, contactSales, contactSales ? properties.getSalesUrl() : null, highlighted);
    }
}
