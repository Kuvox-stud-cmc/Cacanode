package com.cacanode.api.tenant.api;

import java.util.UUID;

public interface PlatformIdentityApi {
    PlatformPrincipal requirePlatformAdministrator(UUID tenantId, UUID userId);

    record PlatformPrincipal(UUID tenantId, UUID userId, String name, String role) {}
}
