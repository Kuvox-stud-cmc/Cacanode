package com.cacanode.api.tenant.api;

public enum TenantKind {
    CUSTOMER,
    PLATFORM_INTERNAL;

    public static TenantKind defaulted(TenantKind value) {
        return value == null ? CUSTOMER : value;
    }
}
