package com.cacanode.api.recruitment.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.recruitment.dto.RecruitmentActivationDtos;
import com.cacanode.api.recruitment.service.RecruitmentCapabilityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recruitment")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','TENANT_ADMIN')")
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class RecruitmentCapabilitiesController extends BaseController {
    private final RecruitmentCapabilityService capabilities;
    @GetMapping("/capabilities")
    public RecruitmentActivationDtos.Capabilities capabilities(HttpServletRequest request) {
        return capabilities.capabilities(getTenantId(request));
    }
}
