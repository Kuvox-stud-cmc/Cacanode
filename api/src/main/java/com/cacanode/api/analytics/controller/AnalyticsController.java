package com.cacanode.api.analytics.controller;

import com.cacanode.api.analytics.api.AnalyticsDtos;
import com.cacanode.api.analytics.api.AnalyticsReadApi;
import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.tenant.api.TenantEntitlementApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalyticsController extends BaseController {
    private final AnalyticsReadApi analyticsReadApi;
    private final TenantEntitlementApi tenantModuleApi;

    @GetMapping("/dashboard/summary")
    public AnalyticsDtos.DashboardSummary dashboardSummary(HttpServletRequest request) {
        return analyticsReadApi.dashboardSummary(getTenantId(request));
    }

    @GetMapping("/analytics")
    public AnalyticsDtos.AnalyticsResponse analytics(
            @RequestParam(defaultValue = "CUSTOMER") String scope,
            @RequestParam(defaultValue = "30") int days,
            HttpServletRequest request) {
        if (days != 7 && days != 30 && days != 90) {
            throw new BadRequestException("days must be one of 7, 30, or 90");
        }
        var tenantId = getTenantId(request);
        if (!tenantModuleApi.getEntitlements(tenantId).advancedAnalytics()) {
            throw new org.springframework.security.access.AccessDeniedException("Advanced analytics requires Pro");
        }
        try {
            return analyticsReadApi.analytics(tenantId,
                    AnalyticsDtos.AnalyticsScope.valueOf(scope.toUpperCase(Locale.ROOT)), days);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("scope must be one of CUSTOMER, EMPLOYEE, or ALL");
        }
    }
}
