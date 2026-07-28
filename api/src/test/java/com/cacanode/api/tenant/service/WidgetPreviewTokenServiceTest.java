package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.tenant.enums.ChatbotStatus;
import com.cacanode.api.tenant.enums.KnowledgeBaseStatus;
import com.cacanode.api.tenant.api.TenantStatus;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.IntegrationToken;
import com.cacanode.api.tenant.model.KnowledgeBase;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WidgetPreviewTokenServiceTest {
    private final IntegrationTokenRepository repository = mock(IntegrationTokenRepository.class);
    private final WidgetPreviewTokenService service = new WidgetPreviewTokenService(
            repository, new ObjectMapper());
    private final UUID tenantId = UUID.randomUUID();
    private final UUID tokenId = UUID.randomUUID();
    private IntegrationToken token;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "signingKey", "test-widget-preview-signing-key");
        ReflectionTestUtils.setField(service, "ttlSeconds", 900L);
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(TenantStatus.ACTIVE);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(UUID.randomUUID());
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
        Chatbot chatbot = new Chatbot();
        chatbot.setId(UUID.randomUUID());
        chatbot.setTenant(tenant);
        chatbot.setKnowledgeBase(knowledgeBase);
        chatbot.setStatus(ChatbotStatus.ACTIVE);
        token = new IntegrationToken();
        token.setId(tokenId);
        token.setTenant(tenant);
        token.setChatbot(chatbot);
        token.setScopes(List.of(IntegrationTokenService.WIDGET_SCOPE));
        when(repository.findWithContextById(tokenId)).thenReturn(Optional.of(token));
    }

    @Test
    void authenticatesShortLivedPreviewWithoutExposingManagedSecret() {
        String preview = service.issue(tenantId, tokenId);

        var principal = service.authenticate(preview);

        assertEquals(tokenId, principal.tokenId());
        assertEquals(tenantId, principal.tenantId());
    }

    @Test
    void revokedManagedTokenInvalidatesPreviewImmediately() {
        String preview = service.issue(tenantId, tokenId);
        token.setRevokedAt(LocalDateTime.now());

        assertThrows(UnauthorizedException.class, () -> service.authenticate(preview));
    }

    @Test
    void tamperedPreviewIsRejected() {
        assertThrows(UnauthorizedException.class,
                () -> service.authenticate(service.issue(tenantId, tokenId) + "tampered"));
    }
}
