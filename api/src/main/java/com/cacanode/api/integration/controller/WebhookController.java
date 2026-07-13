package com.cacanode.api.integration.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.integration.dto.WebhookDtos;
import com.cacanode.api.integration.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/me/integrations/webhooks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class WebhookController extends BaseController {
    private final WebhookService webhookService;

    @GetMapping
    public List<WebhookDtos.Response> list(HttpServletRequest request) {
        return webhookService.list(getTenantId(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookDtos.Created create(
            @Valid @RequestBody WebhookDtos.UpsertRequest body,
            HttpServletRequest request
    ) {
        return webhookService.create(getTenantId(request), body);
    }

    @PutMapping("/{endpointId}")
    public WebhookDtos.Response update(
            @PathVariable UUID endpointId,
            @Valid @RequestBody WebhookDtos.UpsertRequest body,
            HttpServletRequest request
    ) {
        return webhookService.update(getTenantId(request), endpointId, body);
    }

    @PostMapping("/{endpointId}/rotate-secret")
    public WebhookDtos.Created rotate(@PathVariable UUID endpointId, HttpServletRequest request) {
        return webhookService.rotateSecret(getTenantId(request), endpointId);
    }

    @PostMapping("/{endpointId}/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void test(@PathVariable UUID endpointId, HttpServletRequest request) {
        webhookService.enqueueTest(getTenantId(request), endpointId);
    }

    @DeleteMapping("/{endpointId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID endpointId, HttpServletRequest request) {
        webhookService.delete(getTenantId(request), endpointId);
    }
}
