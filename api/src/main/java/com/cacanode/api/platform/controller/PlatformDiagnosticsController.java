package com.cacanode.api.platform.controller;

import com.cacanode.api.platform.api.PlatformDiagnosticsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/platform/operations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(prefix = "app.platform-administration", name = "enabled", havingValue = "true")
public class PlatformDiagnosticsController {
    private final PlatformDiagnosticsApi diagnostics;

    @GetMapping("/health")
    public PlatformDiagnosticsApi.HealthSnapshot health() {
        return diagnostics.health();
    }

    @GetMapping("/queues")
    public PlatformDiagnosticsApi.QueuePage queues(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        return diagnostics.queues(page, size);
    }
}
