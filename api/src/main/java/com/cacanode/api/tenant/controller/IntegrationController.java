package com.cacanode.api.tenant.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.tenant.dto.IntegrationTokenDtos;
import com.cacanode.api.tenant.dto.WidgetConfigDtos;
import com.cacanode.api.tenant.dto.WidgetEmbedDtos;
import com.cacanode.api.tenant.service.IntegrationTokenService;
import com.cacanode.api.tenant.service.WidgetConfigService;
import com.cacanode.api.tenant.service.WidgetIconService;
import com.cacanode.api.tenant.service.ManagedWidgetTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/me/integrations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class IntegrationController extends BaseController {
    private final IntegrationTokenService tokenService;
    private final WidgetConfigService widgetConfigService;
    private final WidgetIconService widgetIconService;
    private final ManagedWidgetTokenService managedWidgetTokenService;

    @GetMapping("/tokens")
    public List<IntegrationTokenDtos.Item> listTokens(HttpServletRequest request) {
        return tokenService.list(getTenantId(request));
    }

    @PostMapping("/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public IntegrationTokenDtos.Created createToken(
            @Valid @RequestBody IntegrationTokenDtos.CreateRequest body,
            HttpServletRequest request
    ) {
        return tokenService.create(getTenantId(request), body);
    }

    @DeleteMapping("/tokens/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeToken(@PathVariable UUID tokenId, HttpServletRequest request) {
        tokenService.revoke(getTenantId(request), tokenId);
    }

    @PostMapping("/tokens/{tokenId}/rotate")
    public IntegrationTokenDtos.Created rotateToken(
            @PathVariable UUID tokenId,
            HttpServletRequest request
    ) {
        return tokenService.rotate(getTenantId(request), tokenId);
    }

    @GetMapping("/widget")
    public WidgetConfigDtos.Response getWidget(HttpServletRequest request) {
        return widgetConfigService.get(getTenantId(request));
    }

    @PutMapping("/widget")
    public WidgetConfigDtos.Response updateWidget(
            @Valid @RequestBody WidgetConfigDtos.UpdateRequest body,
            HttpServletRequest request
    ) {
        return widgetConfigService.update(getTenantId(request), body);
    }

    @GetMapping("/widget/embed")
    public ResponseEntity<WidgetEmbedDtos.Response> getWidgetEmbed(HttpServletRequest request) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(managedWidgetTokenService.getOrCreate(getTenantId(request)));
    }

    @PostMapping(value = "/widget/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WidgetConfigDtos.Response uploadWidgetIcon(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        return widgetIconService.upload(getTenantId(request), file);
    }

    @GetMapping("/widget/icon")
    public ResponseEntity<byte[]> getWidgetIcon(HttpServletRequest request) {
        return iconResponse(widgetIconService.load(getTenantId(request)));
    }

    @DeleteMapping("/widget/icon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWidgetIcon(HttpServletRequest request) {
        widgetIconService.delete(getTenantId(request));
    }

    private ResponseEntity<byte[]> iconResponse(WidgetIconService.WidgetIcon icon) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(icon.contentType()))
                .contentLength(icon.content().length)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(icon.content());
    }
}
