package com.cacanode.api.analytics.controller;

import com.cacanode.api.analytics.api.AnalyticsDtos;
import com.cacanode.api.analytics.api.AnalyticsReadApi;
import com.cacanode.api.tenant.api.TenantEntitlementApi;
import com.cacanode.api.tenant.api.TenantEntitlements;
import com.cacanode.api.tenant.api.TenantPlan;
import com.cacanode.api.tenant.api.TenantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AnalyticsEntitlementTest {
    @Test
    void entitlementIsCheckedBeforeEveryAnalyticsRead() {
        UUID tenantId = UUID.randomUUID();
        AnalyticsReadApi analytics = mock(AnalyticsReadApi.class);
        TenantEntitlementApi entitlements = mock(TenantEntitlementApi.class);
        AnalyticsDtos.AnalyticsResponse response = new AnalyticsDtos.AnalyticsResponse(
                AnalyticsDtos.AnalyticsScope.CUSTOMER, 30, LocalDate.now().minusDays(29), LocalDate.now(),
                new AnalyticsDtos.CountMetric(1, 0, 0), new AnalyticsDtos.DurationMetric(2, 0, 0),
                new AnalyticsDtos.RateMetric(50, 0, 50), new AnalyticsDtos.CountMetric(3, 0, 0),
                null, java.util.List.of(), java.util.List.of());
        when(analytics.analytics(tenantId, AnalyticsDtos.AnalyticsScope.CUSTOMER, 30)).thenReturn(response);
        when(entitlements.getEntitlements(tenantId)).thenReturn(snapshot(tenantId, true), snapshot(tenantId, false));
        AnalyticsController controller = new AnalyticsController(analytics, entitlements);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("tenantId", tenantId.toString());

        assertEquals(response, controller.analytics("CUSTOMER", 30, request));
        assertThrows(AccessDeniedException.class, () -> controller.analytics("CUSTOMER", 30, request));
        verify(analytics, times(1)).analytics(tenantId, AnalyticsDtos.AnalyticsScope.CUSTOMER, 30);
    }

    private TenantEntitlements snapshot(UUID tenantId, boolean analytics) {
        LocalDateTime now = LocalDateTime.now();
        return new TenantEntitlements(tenantId, TenantPlan.PRO, TenantStatus.ACTIVE,
                100, 10, 3, 1024, now, now.plusMonths(1), null,
                true, true, analytics, true);
    }
}
