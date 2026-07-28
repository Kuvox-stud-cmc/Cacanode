package com.cacanode.api.platform.controller;

import com.cacanode.api.analytics.api.PlatformAnalyticsReadApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/overview")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(prefix = "app.platform-administration", name = "enabled", havingValue = "true")
public class PlatformOverviewController {
    private final PlatformAnalyticsReadApi analytics;

    @GetMapping
    public PlatformAnalyticsReadApi.Overview overview(@RequestParam(defaultValue = "30") int days) {
        return analytics.overview(days);
    }
}
