package com.cacanode.api.billing.controller;

import com.cacanode.api.billing.dto.UsageDto;
import com.cacanode.api.billing.service.BillingService;
import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.common.exception.custom.BadRequestException;
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
public class BillingController extends BaseController {
    private final BillingService billingService;

    @GetMapping("/dashboard/summary")
    public UsageDto.DashboardSummary dashboardSummary(HttpServletRequest request) {
        return billingService.dashboardSummary(getTenantId(request));
    }

    @GetMapping("/analytics")
    public UsageDto.AnalyticsResponse analytics(
            @RequestParam(defaultValue = "CUSTOMER") String scope,
            @RequestParam(defaultValue = "30") int days,
            HttpServletRequest request
    ) {
        if (days != 7 && days != 30 && days != 90) {
            throw new BadRequestException("days must be one of 7, 30, or 90");
        }
        try {
            return billingService.analytics(
                    getTenantId(request),
                    UsageDto.AnalyticsScope.valueOf(scope.toUpperCase(Locale.ROOT)),
                    days
            );
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("scope must be one of CUSTOMER, EMPLOYEE, or ALL");
        }
    }
}
