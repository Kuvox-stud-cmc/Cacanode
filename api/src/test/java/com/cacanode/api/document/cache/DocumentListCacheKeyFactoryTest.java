package com.cacanode.api.document.cache;

import com.cacanode.api.common.cache.CacheKeyFactory;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentType;
import com.cacanode.api.document.enums.DocumentVisibility;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentListCacheKeyFactoryTest {
    private final DocumentListCacheKeyFactory factory =
            new DocumentListCacheKeyFactory(new CacheKeyFactory("ccn:v1"));

    @Test
    void canonicalizesUnicodeWhitespaceAndCaseWithoutLeakingSearchText() {
        var decomposed = factory.paged(0, 20, "  CA\u0301CHING  ", DocumentStatus.COMPLETED,
                DocumentType.PDF, DocumentVisibility.EMPLOYEE_ONLY);
        var composed = factory.paged(0, 20, "cáching", DocumentStatus.COMPLETED,
                DocumentType.PDF, DocumentVisibility.EMPLOYEE_ONLY);
        String key = factory.key(UUID.randomUUID(), UUID.randomUUID(), 4, decomposed);

        assertEquals(composed.sha256(), decomposed.sha256());
        assertFalse(key.contains("cáching"));
        assertTrue(key.matches(".*:filters:[0-9a-f]{64}$"));
    }

    @Test
    void legacyUnpagedAndPagedDefaultsNeverCollide() {
        assertNotEquals(factory.legacy().sha256(),
                factory.paged(0, 20, null, null, null, null).sha256());
    }

    @Test
    void tenantKnowledgeBaseGenerationAndFiltersAllChangeTheKey() {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID kb = UUID.randomUUID();
        UUID otherKb = UUID.randomUUID();
        var filters = factory.paged(0, 20, null, null, null, null);

        String base = factory.key(tenant, kb, 0, filters);
        assertNotEquals(base, factory.key(otherTenant, kb, 0, filters));
        assertNotEquals(base, factory.key(tenant, otherKb, 0, filters));
        assertNotEquals(base, factory.key(tenant, kb, 1, filters));
        assertNotEquals(base, factory.key(tenant, kb, 0,
                factory.paged(1, 20, null, null, null, null)));
    }
}
