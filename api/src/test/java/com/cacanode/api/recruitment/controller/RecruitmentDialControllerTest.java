package com.cacanode.api.recruitment.controller;

import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.service.RecruitmentCallDialingService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentDialControllerTest {
    @Test
    void exposesTenantScopedDialEligibilityToTenantAdmins()throws Exception{
        RecruitmentCallDialingService dialing=mock(RecruitmentCallDialingService.class);
        HttpServletRequest request=mock(HttpServletRequest.class);UUID tenantId=UUID.randomUUID(),interviewId=UUID.randomUUID();
        when(request.getAttribute("tenantId")).thenReturn(tenantId.toString());
        var eligibility=new RecruitmentDtos.DialEligibilityResponse(false,"OUTSIDE_DIAL_WINDOW",
                Instant.parse("2026-07-27T01:59:45Z"),Instant.parse("2026-07-27T02:02:00Z"),
                Instant.parse("2026-07-26T12:45:00Z"));
        when(dialing.dialEligibility(tenantId,interviewId)).thenReturn(eligibility);

        var response=new RecruitmentDialController(dialing).dialEligibility(interviewId,request);

        assertSame(eligibility,response.getBody());
        verify(dialing).dialEligibility(tenantId,interviewId);
        assertEquals("hasRole('TENANT_ADMIN')",RecruitmentDialController.class.getMethod("dialEligibility",
                UUID.class,HttpServletRequest.class).getAnnotation(PreAuthorize.class).value());
    }
}
