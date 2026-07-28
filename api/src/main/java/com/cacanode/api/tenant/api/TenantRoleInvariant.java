package com.cacanode.api.tenant.api;

public final class TenantRoleInvariant {
    private TenantRoleInvariant() {}

    public static boolean isValid(String role, TenantKind kind) {
        TenantKind resolved = TenantKind.defaulted(kind);
        return ("PLATFORM_ADMIN".equals(role) && resolved == TenantKind.PLATFORM_INTERNAL)
                || (("TENANT_ADMIN".equals(role) || "USER".equals(role)) && resolved == TenantKind.CUSTOMER);
    }

    public static void requireValid(String role, TenantKind kind) {
        if (!isValid(role, kind)) throw new IllegalStateException("User role is incompatible with tenant kind");
    }
}
