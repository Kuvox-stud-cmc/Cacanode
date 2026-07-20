package com.cacanode.api.tenant.cache;

import com.cacanode.api.common.cache.CacheKeyFactory;
import com.cacanode.api.common.cache.CacheStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class IntegrationTokenCacheInvalidationListener {

    private static final String CACHE_NAME = "integration-token";

    private final CacheStore cacheStore;
    private final CacheKeyFactory keyFactory;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidate(IntegrationTokenCacheInvalidationEvent event) {
        event.tokenHashes().forEach(tokenHash -> cacheStore.delete(
                CACHE_NAME,
                keyFactory.build(CACHE_NAME, tokenHash)
        ));
    }
}
