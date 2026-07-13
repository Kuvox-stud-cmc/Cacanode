package com.cacanode.api.billing.service;

import com.cacanode.api.billing.dto.UsageDto;

import java.util.UUID;

public interface BillingService {
    UsageDto.DashboardSummary dashboardSummary(UUID tenantId);

    UsageDto.AnalyticsResponse analytics(UUID tenantId, UsageDto.AnalyticsScope scope, int days);
}
