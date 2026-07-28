package com.cacanode.api.platform.controller;

import com.cacanode.api.platform.api.PlatformFailureApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/operations/failures")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(prefix = "app.platform-administration", name = "enabled", havingValue = "true")
public class PlatformFailureController {
    private final PlatformFailureApi failures;

    @GetMapping("/summary")
    public PlatformFailureApi.Summary summary(@RequestParam(required = false) UUID tenantId) {
        return failures.summary(tenantId);
    }

    @GetMapping("/{source}")
    public PlatformFailureApi.FailurePage failures(
            @PathVariable String source, @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastSeenAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return failures.failures(source,
                new PlatformFailureApi.FailureQuery(tenantId, state, severity, page, size, sort, direction));
    }
}
