package com.cacanode.api.tenant.api;

import java.util.UUID;

public interface TenantKindApi {
    TenantKind kind(UUID tenantId);

    default boolean isCustomer(UUID tenantId) {
        return kind(tenantId) == TenantKind.CUSTOMER;
    }

    default void requireCustomer(UUID tenantId) {
        if (!isCustomer(tenantId)) {
            throw new IllegalArgumentException("Operation is available only to customer tenants");
        }
    }
}
