package com.cacanode.api.platform.controller;

import com.cacanode.api.analytics.api.PlatformAnalyticsReadApi;
import com.cacanode.api.platform.service.PlatformTenantDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(prefix = "app.platform-administration", name = "enabled", havingValue = "true")
public class PlatformTenantController {
    private static final Set<String> SORTS = Set.of("name", "status", "plan", "createdAt", "updatedAt",
            "activeUsers", "documents", "storageBytes", "conversations", "openTickets", "jobs",
            "verifiedApplications", "completedInterviews");
    private final PlatformAnalyticsReadApi analytics;
    private final PlatformTenantDetailService details;

    @GetMapping
    public PlatformAnalyticsReadApi.TenantPage tenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String plan,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        if (q.length() > 200) throw new IllegalArgumentException("q must not exceed 200 characters");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (!SORTS.contains(sort)) throw new IllegalArgumentException("Unsupported tenant sort");
        if (!Set.of("asc", "desc").contains(direction.toLowerCase(java.util.Locale.ROOT)))
            throw new IllegalArgumentException("direction must be asc or desc");
        return analytics.tenants(new PlatformAnalyticsReadApi.TenantQuery(page, size, q, status, plan, sort, direction));
    }

    @GetMapping("/{tenantId}")
    public PlatformTenantDetailService.Detail tenant(@PathVariable UUID tenantId) {
        return details.tenant(tenantId);
    }
}
