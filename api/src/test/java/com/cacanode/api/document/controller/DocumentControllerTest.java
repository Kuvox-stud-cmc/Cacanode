package com.cacanode.api.document.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.cacanode.api.document.dto.DocumentDownloadResponse;
import com.cacanode.api.document.service.DocumentService;

import jakarta.servlet.http.HttpServletRequest;

class DocumentControllerTest {

    @Test
    void downloadUsesAttachmentHeadersAndRequestTenant() {
        DocumentService service = mock(DocumentService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        UUID tenantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        byte[] content = "hello".getBytes();
        when(request.getAttribute("tenantId")).thenReturn(tenantId.toString());
        when(service.download(tenantId, documentId)).thenReturn(
                new DocumentDownloadResponse("notes.txt", "text/plain", content));

        var response = new DocumentController(service).download(documentId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("text/plain", response.getHeaders().getContentType().toString());
        assertEquals(5, response.getHeaders().getContentLength());
        String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(contentDisposition.startsWith("attachment;"));
        assertTrue(contentDisposition.contains("filename=\"notes.txt\""));
        assertArrayEquals(content, response.getBody());
        verify(service).download(tenantId, documentId);
    }
}
