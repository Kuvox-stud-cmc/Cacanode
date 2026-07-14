package com.cacanode.api.document.controller;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.document.dto.DocumentListItemResponse;
import com.cacanode.api.document.dto.DocumentStatusResponse;
import com.cacanode.api.document.dto.DocumentUploadResponse;
import com.cacanode.api.document.dto.DocumentVisibilityUpdateRequest;
import com.cacanode.api.document.enums.DocumentVisibility;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentType;
import jakarta.validation.Valid;
import com.cacanode.api.document.service.DocumentService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController extends BaseController {

    private final DocumentService documentService;

    @GetMapping
    public List<DocumentListItemResponse> list(
            @RequestParam("knowledgeBaseId") UUID knowledgeBaseId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "status", required = false) DocumentStatus status,
            @RequestParam(value = "type", required = false) DocumentType type,
            @RequestParam(value = "visibility", required = false) DocumentVisibility visibility,
            HttpServletRequest request
    ) {
        UUID tenantId = getTenantId(request);
        if (page == null && size == null && query == null && status == null && type == null
                && visibility == null) {
            return documentService.list(tenantId, knowledgeBaseId);
        }
        return documentService.list(
                tenantId,
                knowledgeBaseId,
                page,
                size,
                query,
                status,
                type,
                visibility
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") UUID knowledgeBaseId,
            @RequestParam("visibility") DocumentVisibility visibility,
            HttpServletRequest request
    ) {
        DocumentUploadResponse response = documentService.upload(
                getTenantId(request),
                getUserId(request),
                getRole(request),
                knowledgeBaseId,
                visibility,
                file
        );
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{documentId}")
    public DocumentStatusResponse get(
            @PathVariable UUID documentId,
            HttpServletRequest request
    ) {
        return documentService.get(getTenantId(request), documentId);
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID documentId,
            HttpServletRequest request
    ) {
        var download = documentService.download(getTenantId(request), documentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    @PatchMapping("/{documentId}/visibility")
    public DocumentStatusResponse updateVisibility(
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentVisibilityUpdateRequest body,
            HttpServletRequest request
    ) {
        return documentService.updateVisibility(
                getTenantId(request), getRole(request), documentId, body.visibility());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID documentId,
            HttpServletRequest request
    ) {
        documentService.delete(getTenantId(request), getRole(request), documentId);
        return ResponseEntity.noContent().build();
    }
}
