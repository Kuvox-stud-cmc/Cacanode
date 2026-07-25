package com.cacanode.api.recruitment.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.recruitment.dto.RecruitmentActivationDtos;
import com.cacanode.api.recruitment.service.RecruitmentCapabilityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/recruitment/tenants/{tenantId}/activation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class PlatformRecruitmentActivationController extends BaseController {
    private final RecruitmentCapabilityService service;
    @GetMapping public RecruitmentActivationDtos.Activation get(@PathVariable UUID tenantId){return service.activation(tenantId);}
    @PutMapping public RecruitmentActivationDtos.Activation update(@PathVariable UUID tenantId,
            @Valid @RequestBody RecruitmentActivationDtos.ActivationUpdate body,HttpServletRequest request){
        return service.update(tenantId,getUserId(request),body,request.getRemoteAddr(),request.getHeader("User-Agent"));
    }
}
