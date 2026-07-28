package com.cacanode.api.tenant.api;

import java.util.UUID;

public interface TenantEntitlementApi {
    TenantEntitlements getEntitlements(UUID tenantId);

    TenantEntitlements lockEntitlements(UUID tenantId);

    void applyEntitlements(ApplyTenantEntitlementsCommand command);
}
