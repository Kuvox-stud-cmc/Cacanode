package com.cacanode.api.document.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.document.dto.DocumentListItemResponse;
import com.cacanode.api.document.dto.DocumentStatusResponse;
import com.cacanode.api.document.dto.DocumentUploadResponse;
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
            HttpServletRequest request
    ) {
        return documentService.list(getTenantId(request), knowledgeBaseId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") UUID knowledgeBaseId,
            HttpServletRequest request
    ) {
        DocumentUploadResponse response = documentService.upload(
                getTenantId(request),
                getUserId(request),
                knowledgeBaseId,
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
}
