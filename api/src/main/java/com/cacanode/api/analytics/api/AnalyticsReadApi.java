package com.cacanode.api.analytics.api;

import java.util.UUID;

public interface AnalyticsReadApi {
    AnalyticsDtos.DashboardSummary dashboardSummary(UUID tenantId);

    AnalyticsDtos.AnalyticsResponse analytics(
            UUID tenantId, AnalyticsDtos.AnalyticsScope scope, int days);

    AnalyticsDtos.RecruitmentAnalyticsResponse recruitment(UUID tenantId, int days);
}
