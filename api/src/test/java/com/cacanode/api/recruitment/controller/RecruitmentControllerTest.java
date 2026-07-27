package com.cacanode.api.recruitment.controller;

import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.query.RecruitmentQueryService;
import com.cacanode.api.recruitment.service.RecruitmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import org.springframework.http.CacheControl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentControllerTest {
    @Test
    void employeeBoundaryExcludesPlatformAdminAndSettingsMutationIsAdminOnly()throws Exception{
        assertEquals("hasAnyRole('USER','TENANT_ADMIN')",RecruitmentController.class.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('TENANT_ADMIN')",RecruitmentController.class.getMethod("updateSettings",
                RecruitmentDtos.SettingsUpdate.class,HttpServletRequest.class).getAnnotation(PreAuthorize.class).value());
        ConditionalOnProperty condition=RecruitmentController.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals("app.recruitment",condition.prefix());assertEquals("enabled",condition.name()[0]);
    }

    @Test
    void listKeepsArrayBodyAndExposesTotalCount(){
        RecruitmentService service=mock(RecruitmentService.class);RecruitmentQueryService queries=mock(RecruitmentQueryService.class);
        HttpServletRequest request=mock(HttpServletRequest.class);UUID tenantId=UUID.randomUUID();when(request.getAttribute("tenantId")).thenReturn(tenantId.toString());
        when(queries.jobs(tenantId,0,20,null,null,null,null,null,null,null,null,null,null,null)).thenReturn(new RecruitmentDtos.PageResult<>(List.of(),42));
        var response=new RecruitmentController(service,queries).jobs(0,20,null,null,null,null,null,null,null,null,null,null,null,request);
        assertEquals("42",response.getHeaders().getFirst("X-Total-Count"));assertNotNull(response.getBody());assertTrue(response.getBody().isEmpty());
    }

    @Test
    void previewIsTenantScopedAndNeverCacheable(){
        RecruitmentService service=mock(RecruitmentService.class);RecruitmentQueryService queries=mock(RecruitmentQueryService.class);
        HttpServletRequest request=mock(HttpServletRequest.class);UUID tenantId=UUID.randomUUID(),jobId=UUID.randomUUID(),publicId=UUID.randomUUID();
        when(request.getAttribute("tenantId")).thenReturn(tenantId.toString());
        var preview=new RecruitmentDtos.JobPreview(publicId,"acme","Acme","Engineer","Plain","<p>Plain</p>",
                null,null,null,null,null,"en-US",com.cacanode.api.recruitment.model.RecruitmentEnums.CvPolicy.OPTIONAL,
                com.cacanode.api.recruitment.model.RecruitmentEnums.JobStatus.DRAFT,null,LocalDateTime.now().plusDays(3));
        when(service.preview(tenantId,jobId)).thenReturn(preview);
        var response=new RecruitmentController(service,queries).preview(jobId,request);
        assertEquals("no-store",response.getHeaders().getCacheControl());
        assertSame(preview,response.getBody());
        verify(service).preview(tenantId,jobId);
    }
}
