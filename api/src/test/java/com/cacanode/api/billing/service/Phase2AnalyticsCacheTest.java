package com.cacanode.api.billing.service;

import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.dto.UsageDto;
import com.cacanode.api.billing.gateway.PaymentGateway;
import com.cacanode.api.billing.repository.BillingPaymentOrderRepository;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.repository.BillingWebhookEventRepository;
import com.cacanode.api.common.cache.*;
import com.cacanode.api.common.config.CacheProperties;
import com.cacanode.api.tenant.api.TenantEntitlements;
import com.cacanode.api.tenant.api.TenantModuleApi;
import com.cacanode.api.tenant.enums.TenantPlan;
import com.cacanode.api.tenant.enums.TenantStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class Phase2AnalyticsCacheTest {
    @Test
    void entitlementIsCheckedBeforeEveryAnalyticsCacheHit() {
        UUID tenantId = UUID.randomUUID();
        BillingService analytics = mock(BillingService.class);
        UsageDto.AnalyticsResponse response = new UsageDto.AnalyticsResponse(
                UsageDto.AnalyticsScope.CUSTOMER, 30, LocalDate.now().minusDays(29), LocalDate.now(),
                new UsageDto.CountMetric(1, 0, 100), new UsageDto.DurationMetric(2, 0, 100),
                new UsageDto.RateMetric(50, 0, 50), new UsageDto.CountMetric(3, 0, 100),
                null, java.util.List.of(), java.util.List.of());
        when(analytics.analytics(tenantId, UsageDto.AnalyticsScope.CUSTOMER, 30)).thenReturn(response);
        TenantModuleApi tenants = mock(TenantModuleApi.class);
        when(tenants.getEntitlements(tenantId)).thenReturn(
                entitlements(tenantId, true), entitlements(tenantId, false));
        BillingProperties billingProperties = new BillingProperties();
        BillingFacade facade = new BillingFacade(
                analytics, new BillingPlanCatalog(billingProperties), billingProperties,
                mock(BillingSubscriptionRepository.class), mock(BillingPaymentOrderRepository.class),
                mock(BillingWebhookEventRepository.class), tenants, mock(PaymentGateway.class),
                new BillingPeriods(), mock(JdbcTemplate.class), new ObjectMapper(),
                mock(org.springframework.context.ApplicationEventPublisher.class));
        CacheProperties cacheProperties = enabledAnalytics();
        VersionedJsonCache cache = new VersionedJsonCache(
                new MemoryStore(), cacheProperties, mock(CacheMetrics.class),
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(facade, "businessCache", cache);
        ReflectionTestUtils.setField(facade, "cacheKeyFactory", new CacheKeyFactory("ccn:v1"));

        assertEquals(response, facade.analytics(tenantId, UsageDto.AnalyticsScope.CUSTOMER, 30));
        assertThrows(AccessDeniedException.class,
                () -> facade.analytics(tenantId, UsageDto.AnalyticsScope.CUSTOMER, 30));
        verify(analytics, times(1)).analytics(tenantId, UsageDto.AnalyticsScope.CUSTOMER, 30);
        verify(tenants, times(2)).getEntitlements(tenantId);
    }

    private static CacheProperties enabledAnalytics() {
        CacheProperties properties = new CacheProperties();
        properties.setEnabled(true);
        properties.setBusinessReadEnabled(true);
        properties.setAnalyticsEnabled(true);
        return properties;
    }

    private static TenantEntitlements entitlements(UUID tenantId, boolean analytics) {
        LocalDateTime now = LocalDateTime.now();
        return new TenantEntitlements(tenantId, TenantPlan.PRO, TenantStatus.ACTIVE,
                100, 10, 3, 1024, now, now.plusMonths(1), null,
                true, true, analytics, true);
    }

    private static final class MemoryStore implements CacheStore {
        private final Map<String, byte[]> values = new HashMap<>();
        public CacheReadResult get(String cacheName, String key) {
            return values.containsKey(key) ? CacheReadResult.hit(values.get(key))
                    : CacheReadResult.of(CacheReadStatus.MISS);
        }
        public CacheOperationStatus put(String cacheName, String key, byte[] value, Duration ttl) {
            values.put(key, value.clone());
            return CacheOperationStatus.SUCCESS;
        }
        public CacheOperationStatus delete(String cacheName, String key) {
            values.remove(key);
            return CacheOperationStatus.SUCCESS;
        }
    }
}
