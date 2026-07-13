package com.cacanode.api.tenant.controller;

import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.tenant.dto.WidgetConfigDtos;
import com.cacanode.api.tenant.service.IntegrationTokenService;
import com.cacanode.api.tenant.service.WidgetConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/widget")
@RequiredArgsConstructor
public class PublicWidgetController {
    private final IntegrationTokenService tokenService;
    private final WidgetConfigService widgetConfigService;

    @GetMapping("/config")
    public WidgetConfigDtos.Response config(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Parent-Origin", required = false) String parentOrigin
    ) {
        var principal = tokenService.authenticate(authorization, IntegrationTokenService.WIDGET_SCOPE);
        WidgetConfigDtos.Response response = widgetConfigService.get(principal.tenantId());
        if (!response.active()) {
            throw new UnauthorizedException("Widget is inactive");
        }
        if (!response.allowedOrigins().isEmpty()
                && (parentOrigin == null || !response.allowedOrigins().contains(parentOrigin))) {
            throw new UnauthorizedException("Website origin is not allowed");
        }
        return response;
    }
}
