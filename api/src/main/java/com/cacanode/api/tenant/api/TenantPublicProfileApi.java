package com.cacanode.api.tenant.api;

import java.util.UUID;

public interface TenantPublicProfileApi {
    TenantPublicProfile getPublicProfile(UUID tenantId);

    record TenantPublicProfile(UUID tenantId, String slug, String companyName, TenantStatus status) {
    }
}
