package com.cacanode.api.common.cache;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BusinessCacheInvalidationListenerTest {
    @Test
    void deletesOnlyExactTenantKeysAndIncrementsExactDocumentGeneration() {
        CacheStore store = mock(CacheStore.class);
        DocumentListGenerationStore generations = mock(DocumentListGenerationStore.class);
        CacheKeyFactory keys = new CacheKeyFactory("ccn:v1");
        BusinessCacheInvalidationListener listener = new BusinessCacheInvalidationListener(store, keys, generations);
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();

        listener.invalidate(new BusinessCacheInvalidationEvent(tenantId, Set.of(
                BusinessCache.WIDGET_CONFIG, BusinessCache.BILLING_ACCOUNT, BusinessCache.DASHBOARD)));
        listener.invalidateDocuments(new DocumentListInvalidationEvent(tenantId, knowledgeBaseId));

        verify(store).delete("widget-config", "ccn:v1:widget-config:tenant:" + tenantId);
        verify(store).delete("billing-account", "ccn:v1:billing-account:tenant:" + tenantId);
        verify(store).delete("dashboard", "ccn:v1:dashboard-summary:tenant:" + tenantId);
        verify(generations).increment(tenantId, knowledgeBaseId);
    }
}
