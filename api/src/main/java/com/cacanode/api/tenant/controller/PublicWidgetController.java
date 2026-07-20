package com.cacanode.api.tenant.controller;

import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.tenant.dto.WidgetConfigDtos;
import com.cacanode.api.tenant.service.IntegrationTokenService;
import com.cacanode.api.tenant.service.WidgetConfigService;
import com.cacanode.api.tenant.service.WidgetIconService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final WidgetIconService widgetIconService;

    @GetMapping("/config")
    public WidgetConfigDtos.Response config(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Parent-Origin", required = false) String parentOrigin
    ) {
        return authorize(authorization, parentOrigin).config();
    }

    @GetMapping("/icon")
    public ResponseEntity<byte[]> icon(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Parent-Origin", required = false) String parentOrigin
    ) {
        var access = authorize(authorization, parentOrigin);
        var icon = widgetIconService.load(access.principal().tenantId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(icon.contentType()))
                .contentLength(icon.content().length)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(icon.content());
    }

    private AuthorizedWidget authorize(String authorization, String parentOrigin) {
        var principal = tokenService.authenticate(authorization, IntegrationTokenService.WIDGET_SCOPE);
        WidgetConfigDtos.Response response = widgetConfigService.get(principal.tenantId());
        if (!response.active()) {
            throw new UnauthorizedException("Widget is inactive");
        }
        if (!response.allowedOrigins().isEmpty()
                && (parentOrigin == null || !response.allowedOrigins().contains(parentOrigin))) {
            throw new UnauthorizedException("Website origin is not allowed");
        }
        return new AuthorizedWidget(principal, response);
    }

    private record AuthorizedWidget(
            IntegrationTokenService.Principal principal,
            WidgetConfigDtos.Response config
    ) { }
}
