package com.cacanode.api.document.service;

import com.cacanode.api.ai.api.AiInferenceApi;
import com.cacanode.api.document.api.DocumentApi;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentVisibility;
import com.cacanode.api.document.model.Document;
import com.cacanode.api.document.repository.DocumentRepository;
import com.cacanode.api.tenant.api.IntegrationAccessApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicEvidenceServiceTest {
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final IntegrationAccessApi integrationAccessApi = mock(IntegrationAccessApi.class);
    private final AiInferenceApi inferenceClient = mock(AiInferenceApi.class);
    private final PublicEvidenceService service = new PublicEvidenceService(
            documentRepository, integrationAccessApi,
            inferenceClient, new ObjectMapper());

    private final UUID tenantId = UUID.randomUUID();
    private final UUID knowledgeBaseId = UUID.randomUUID();
    private final UUID chatbotId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "signingKey", "test-public-evidence-signing-key");
        ReflectionTestUtils.setField(service, "webBaseUrl", "https://app.example");
        ReflectionTestUtils.setField(service, "ttlSeconds", 3600L);

        Document document = new Document();
        document.setId(documentId);
        document.setTenantId(tenantId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileName("returns-policy.pdf");
        document.setStatus(DocumentStatus.COMPLETED);
        document.setVisibility(DocumentVisibility.CUSTOMER_AND_EMPLOYEE);

        when(documentRepository.findByIdAndTenantIdAndKnowledgeBaseIdAndStatusAndVisibility(
                documentId, tenantId, knowledgeBaseId,
                DocumentStatus.COMPLETED, DocumentVisibility.CUSTOMER_AND_EMPLOYEE))
                .thenReturn(Optional.of(document));
        when(inferenceClient.listDocumentUnits(
                eq(tenantId), eq(knowledgeBaseId), eq(documentId), any()))
                .thenReturn(List.of(new AiInferenceApi.DocumentUnit(
                        "unit-1", 0, "Returns are accepted within seven days.",
                        "returns-policy.pdf", "text", "paragraph", List.of(),
                        null, 1, null, null, null, null, null)));
    }

    @Test
    void signedLinkLoadsOnlyValidatedIndexedContent() {
        String signedToken = issue().substring("https://app.example/evidence/".length());

        var response = service.load(signedToken, "request-1");

        assertEquals(documentId, response.documentId());
        assertEquals("returns-policy.pdf", response.sourceName());
        assertEquals("unit:unit-1", response.focus());
        assertEquals(1, response.units().size());
    }

    @Test
    void apiScopedLinkDoesNotRequireAnActiveWidget() {
        String signedToken = issue().substring("https://app.example/evidence/".length());

        var response = service.load(signedToken, "request-1");

        assertEquals(documentId, response.documentId());
        assertEquals(1, response.units().size());
    }

    @Test
    void tamperedLinkIsRejected() {
        String signedToken = issue().substring("https://app.example/evidence/".length());

        assertThrows(ResourceNotFoundException.class,
                () -> service.load(signedToken + "tampered", "request-1"));
    }

    @Test
    void revokingBoundWidgetTokenInvalidatesExistingLink() {
        String signedToken = issue().substring("https://app.example/evidence/".length());
        org.mockito.Mockito.doThrow(new RuntimeException("revoked")).when(integrationAccessApi)
                .validateEvidenceAccess(tokenId, tenantId, knowledgeBaseId);

        assertThrows(ResourceNotFoundException.class,
                () -> service.load(signedToken, "request-1"));
    }

    @Test
    void hiddenOrIncompleteDocumentIsRejected() {
        String signedToken = issue().substring("https://app.example/evidence/".length());
        when(documentRepository.findByIdAndTenantIdAndKnowledgeBaseIdAndStatusAndVisibility(
                documentId, tenantId, knowledgeBaseId,
                DocumentStatus.COMPLETED, DocumentVisibility.CUSTOMER_AND_EMPLOYEE))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.load(signedToken, "request-1"));
    }

    @Test
    void expiredLinkIsRejected() {
        ReflectionTestUtils.setField(service, "ttlSeconds", -1L);
        String signedToken = issue().substring("https://app.example/evidence/".length());

        assertThrows(ResourceNotFoundException.class,
                () -> service.load(signedToken, "request-1"));
    }

    private String issue() {
        return service.issue(tenantId, knowledgeBaseId, tokenId, new DocumentApi.EvidenceCitation(
                documentId.toString(), "unit-1", 0, 1, null, null, null));
    }
}
