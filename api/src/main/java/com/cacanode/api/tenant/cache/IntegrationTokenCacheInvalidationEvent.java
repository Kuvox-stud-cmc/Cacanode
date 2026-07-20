package com.cacanode.api.tenant.cache;

import java.util.Set;

public record IntegrationTokenCacheInvalidationEvent(Set<String> tokenHashes) {

    public IntegrationTokenCacheInvalidationEvent {
        tokenHashes = Set.copyOf(tokenHashes);
    }
}
