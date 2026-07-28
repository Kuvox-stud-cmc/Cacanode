package com.cacanode.api.recruitment.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.recruitment.dto.RecruitmentPrivacyDtos;
import com.cacanode.api.recruitment.service.RecruitmentPrivacyDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruitment")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class RecruitmentPrivacyController extends BaseController {
    private final RecruitmentPrivacyDeletionService privacy;
    @PostMapping("/applications/{id}/privacy-deletion-requests")
    public RecruitmentPrivacyDtos.Status request(@PathVariable UUID id,
            @Valid @RequestBody RecruitmentPrivacyDtos.AdminRequest body,HttpServletRequest request){
        return privacy.requestByAdmin(getTenantId(request),id,getUserId(request),body.verificationReference());
    }
    @GetMapping("/privacy-deletion-requests")
    public List<RecruitmentPrivacyDtos.Status> list(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="50") int size,HttpServletRequest request){return privacy.list(getTenantId(request),page,size);}
}
