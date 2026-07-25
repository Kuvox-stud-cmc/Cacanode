package com.cacanode.api.recruitment.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.event.AuditLogEvent;
import com.cacanode.api.recruitment.query.RecruitmentCvStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruitment/applications/{applicationId}/cv")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','TENANT_ADMIN')")
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class RecruitmentCvController extends BaseController {
    private final RecruitmentCvStorageService cvs;
    private final ApplicationEventPublisher events;

    @GetMapping
    public ResponseEntity<byte[]> download(@PathVariable UUID applicationId,HttpServletRequest request){
        UUID tenantId=getTenantId(request);var file=cvs.download(tenantId,applicationId);
        audit(tenantId,getUserId(request),applicationId,LogAction.RECRUITMENT_CV_DOWNLOADED,request);
        ContentDisposition disposition=ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString())
                .cacheControl(CacheControl.noStore()).body(file.content());
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable UUID applicationId,HttpServletRequest request){
        UUID tenantId=getTenantId(request);cvs.scheduleImmediateDeletion(tenantId,applicationId);cvs.deleteNow(tenantId,applicationId);
        audit(tenantId,getUserId(request),applicationId,LogAction.RECRUITMENT_CV_DELETED,request);
        return ResponseEntity.noContent().build();
    }

    private void audit(UUID tenantId,UUID userId,UUID applicationId,LogAction action,HttpServletRequest request){
        events.publishEvent(AuditLogEvent.builder(this).tenantId(tenantId).userId(userId).action(action)
                .resourceType("recruitment_application").resourceId(applicationId)
                .ipAddress(request.getRemoteAddr()).userAgent(request.getHeader("User-Agent"))
                .metadata(Map.of()).build());
    }
}
