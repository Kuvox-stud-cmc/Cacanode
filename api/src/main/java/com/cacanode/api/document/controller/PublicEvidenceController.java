package com.cacanode.api.document.controller;

import com.cacanode.api.document.dto.PublicEvidenceDtos;
import com.cacanode.api.document.service.PublicEvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/evidence")
@RequiredArgsConstructor
public class PublicEvidenceController {
    private final PublicEvidenceService evidenceService;

    @GetMapping("/{signedToken}")
    public ResponseEntity<PublicEvidenceDtos.Response> get(
            @PathVariable String signedToken,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(evidenceService.load(signedToken, requestId));
    }
}
