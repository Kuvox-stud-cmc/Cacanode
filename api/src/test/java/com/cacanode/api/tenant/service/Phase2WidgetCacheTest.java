package com.cacanode.api.tenant.service;

import com.cacanode.api.common.cache.*;
import com.cacanode.api.common.config.CacheProperties;
import com.cacanode.api.tenant.api.TenantEntitlements;
import com.cacanode.api.tenant.api.TenantEntitlementApi;
import com.cacanode.api.tenant.cache.IntegrationTokenCacheInvalidationPublisher;
import com.cacanode.api.tenant.dto.WidgetConfigDtos;
import com.cacanode.api.tenant.api.TenantPlan;
import com.cacanode.api.tenant.api.TenantStatus;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class Phase2WidgetCacheTest {
    @Test
    void repeatedReadsShareEquivalentDtoButDifferentTenantsNeverShareKeys() {
        UUID firstTenant = UUID.randomUUID();
        UUID secondTenant = UUID.randomUUID();
        WidgetConfigRepository repository = mock(WidgetConfigRepository.class);
        when(repository.findFirstByTenant_IdOrderByCreatedAtAsc(firstTenant))
                .thenReturn(Optional.of(config(firstTenant, "First")));
        when(repository.findFirstByTenant_IdOrderByCreatedAtAsc(secondTenant))
                .thenReturn(Optional.of(config(secondTenant, "Second")));
        TenantEntitlementApi tenants = mock(TenantEntitlementApi.class);
        when(tenants.getEntitlements(any())).thenAnswer(invocation -> entitlements(invocation.getArgument(0)));
        WidgetConfigService service = new WidgetConfigService(
                repository, tenants, mock(IntegrationTokenCacheInvalidationPublisher.class));
        CacheProperties properties = new CacheProperties();
        properties.setEnabled(true);
        properties.setBusinessReadEnabled(true);
        properties.setWidgetConfigEnabled(true);
        ReflectionTestUtils.setField(service, "businessCache", new VersionedJsonCache(
                new MemoryStore(), properties, mock(CacheMetrics.class),
                new ObjectMapper().findAndRegisterModules()));
        ReflectionTestUtils.setField(service, "cacheKeyFactory", new CacheKeyFactory("ccn:v1"));

        WidgetConfigDtos.Response first = service.get(firstTenant);
        assertEquals(first, service.get(firstTenant));
        assertEquals("Second", service.get(secondTenant).displayName());

        verify(repository, times(1)).findFirstByTenant_IdOrderByCreatedAtAsc(firstTenant);
        verify(repository, times(1)).findFirstByTenant_IdOrderByCreatedAtAsc(secondTenant);
    }

    private static WidgetConfig config(UUID tenantId, String name) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        Chatbot chatbot = new Chatbot();
        chatbot.setId(UUID.randomUUID());
        chatbot.setAllowedOrigins(List.of("https://example.com"));
        WidgetConfig config = new WidgetConfig();
        config.setTenant(tenant);
        config.setChatbot(chatbot);
        config.setDisplayName(name);
        config.setWelcomeMessage("Hello");
        return config;
    }

    private static TenantEntitlements entitlements(UUID tenantId) {
        LocalDateTime now = LocalDateTime.now();
        return new TenantEntitlements(tenantId, TenantPlan.PRO, TenantStatus.ACTIVE,
                100, 10, 3, 1024, now, now.plusMonths(1), null,
                true, true, true, true);
    }

    private static final class MemoryStore implements CacheStore {
        private final Map<String, byte[]> values = new HashMap<>();
        public CacheReadResult get(String cacheName, String key) {
            byte[] value = values.get(key);
            return value == null ? CacheReadResult.of(CacheReadStatus.MISS) : CacheReadResult.hit(value);
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
