package com.cacanode.api.tenant.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.tenant.dto.CustomerAnswerPromptDtos;
import com.cacanode.api.tenant.service.CustomerAnswerPromptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/me/customer-answer-prompt")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class CustomerAnswerPromptController extends BaseController {
    private final CustomerAnswerPromptService customerAnswerPromptService;

    @GetMapping
    public CustomerAnswerPromptDtos.Response get(HttpServletRequest request) {
        return customerAnswerPromptService.get(getTenantId(request));
    }

    @PutMapping
    public CustomerAnswerPromptDtos.Response update(
            @Valid @RequestBody CustomerAnswerPromptDtos.UpdateRequest body,
            HttpServletRequest request
    ) {
        return customerAnswerPromptService.update(getTenantId(request), getUserId(request), body.prompt());
    }
}
