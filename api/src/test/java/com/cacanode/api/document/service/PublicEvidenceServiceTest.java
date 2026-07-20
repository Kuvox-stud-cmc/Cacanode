package com.cacanode.api.document.service;

import com.cacanode.api.chat.ai.AiInferenceClient;
import com.cacanode.api.chat.dto.ChatDtos;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentVisibility;
import com.cacanode.api.document.model.Document;
import com.cacanode.api.document.repository.DocumentRepository;
import com.cacanode.api.tenant.enums.ChatbotStatus;
import com.cacanode.api.tenant.enums.KnowledgeBaseStatus;
import com.cacanode.api.tenant.enums.TenantStatus;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.IntegrationToken;
import com.cacanode.api.tenant.model.KnowledgeBase;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import com.cacanode.api.tenant.service.IntegrationTokenService;
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
    private final IntegrationTokenRepository tokenRepository = mock(IntegrationTokenRepository.class);
    private final WidgetConfigRepository widgetConfigRepository = mock(WidgetConfigRepository.class);
    private final AiInferenceClient inferenceClient = mock(AiInferenceClient.class);
    private final PublicEvidenceService service = new PublicEvidenceService(
            documentRepository, tokenRepository, widgetConfigRepository,
            inferenceClient, new ObjectMapper());

    private final UUID tenantId = UUID.randomUUID();
    private final UUID knowledgeBaseId = UUID.randomUUID();
    private final UUID chatbotId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private IntegrationToken integrationToken;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "signingKey", "test-public-evidence-signing-key");
        ReflectionTestUtils.setField(service, "webBaseUrl", "https://app.example");
        ReflectionTestUtils.setField(service, "ttlSeconds", 3600L);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(knowledgeBaseId);
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
        Chatbot chatbot = new Chatbot();
        chatbot.setId(chatbotId);
        chatbot.setStatus(ChatbotStatus.ACTIVE);
        chatbot.setKnowledgeBase(knowledgeBase);
        chatbot.setTenant(tenant);
        integrationToken = new IntegrationToken();
        integrationToken.setId(tokenId);
        integrationToken.setTenant(tenant);
        integrationToken.setChatbot(chatbot);
        integrationToken.setScopes(List.of(IntegrationTokenService.WIDGET_SCOPE));

        WidgetConfig config = new WidgetConfig();
        config.setActive(true);
        Document document = new Document();
        document.setId(documentId);
        document.setTenantId(tenantId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileName("returns-policy.pdf");
        document.setStatus(DocumentStatus.COMPLETED);
        document.setVisibility(DocumentVisibility.CUSTOMER_AND_EMPLOYEE);

        when(tokenRepository.findWithContextById(tokenId)).thenReturn(Optional.of(integrationToken));
        when(widgetConfigRepository.findByChatbot_IdAndTenant_Id(chatbotId, tenantId))
                .thenReturn(Optional.of(config));
        when(documentRepository.findByIdAndTenantIdAndKnowledgeBaseIdAndStatusAndVisibility(
                documentId, tenantId, knowledgeBaseId,
                DocumentStatus.COMPLETED, DocumentVisibility.CUSTOMER_AND_EMPLOYEE))
                .thenReturn(Optional.of(document));
        when(inferenceClient.listDocumentUnits(
                eq(tenantId), eq(knowledgeBaseId), eq(documentId), any()))
                .thenReturn(List.of(new ChatDtos.DocumentUnitResponse(
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
        integrationToken.setScopes(List.of(IntegrationTokenService.API_SCOPE));
        when(widgetConfigRepository.findByChatbot_IdAndTenant_Id(chatbotId, tenantId))
                .thenReturn(Optional.empty());
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
        integrationToken.setRevokedAt(LocalDateTime.now());

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
        return service.issue(tenantId, knowledgeBaseId, tokenId, new ChatDtos.CitationResponse(
                "S1", documentId.toString(), "returns-policy.pdf", 1, 0, 0.9,
                "Returns are accepted within seven days.", "unit-1", "text", List.of(),
                "paragraph", null, null, null, null));
    }
}
