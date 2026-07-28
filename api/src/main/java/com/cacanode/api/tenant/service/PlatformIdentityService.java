package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.tenant.api.PlatformIdentityApi;
import com.cacanode.api.tenant.api.TenantKind;
import com.cacanode.api.tenant.enums.UserRole;
import com.cacanode.api.tenant.enums.UserStatus;
import com.cacanode.api.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformIdentityService implements PlatformIdentityApi {
    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public PlatformPrincipal requirePlatformAdministrator(UUID tenantId, UUID userId) {
        var user = users.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new UnauthorizedException("Platform administrator identity is invalid"));
        if (user.getTenant().getKind() != TenantKind.PLATFORM_INTERNAL
                || user.getRole() != UserRole.PLATFORM_ADMIN
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Platform administrator identity is invalid");
        }
        return new PlatformPrincipal(tenantId, userId, user.getFullName(), user.getRole().name());
    }
}
