package com.cacanode.api.platform.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.recruitment.api.RecruitmentPlatformAdministrationApi;
import com.cacanode.api.tenant.api.TenantKindApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/recruitment/tenants/{tenantId}/activation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(prefix = "app.platform-administration", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "app.recruitment", name = "enabled", havingValue = "true")
public class PlatformRecruitmentActivationController extends BaseController {
    private final RecruitmentPlatformAdministrationApi recruitment;
    private final TenantKindApi tenantKinds;

    @GetMapping
    public RecruitmentPlatformAdministrationApi.Activation get(@PathVariable UUID tenantId) {
        requireCustomer(tenantId);
        return recruitment.activation(tenantId);
    }

    @PutMapping
    public RecruitmentPlatformAdministrationApi.Activation update(
            @PathVariable UUID tenantId,
            @Valid @RequestBody RecruitmentPlatformAdministrationApi.ActivationUpdate body,
            HttpServletRequest request) {
        requireCustomer(tenantId);
        return recruitment.update(tenantId, getUserId(request), body,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private void requireCustomer(UUID tenantId) {
        try {
            tenantKinds.requireCustomer(tenantId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
