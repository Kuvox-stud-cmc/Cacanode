package com.cacanode.api.recruitment.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.service.RecruitmentCallDialingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruitment/interviews")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="calling-enabled",havingValue="true")
public class RecruitmentDialController extends BaseController {
    private final RecruitmentCallDialingService dialing;

    @GetMapping("/{id}/dial-eligibility")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<RecruitmentDtos.DialEligibilityResponse> dialEligibility(
            @PathVariable UUID id,HttpServletRequest request) {
        return ResponseEntity.ok(dialing.dialEligibility(getTenantId(request),id));
    }

    @PostMapping("/{id}/dial")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<RecruitmentDtos.DialResponse> dial(@PathVariable UUID id,HttpServletRequest request) {
        return ResponseEntity.accepted().body(dialing.dial(getTenantId(request),id));
    }
}
