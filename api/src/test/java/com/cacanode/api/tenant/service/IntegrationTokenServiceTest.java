package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.tenant.dto.IntegrationTokenDtos;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.IntegrationToken;
import com.cacanode.api.tenant.model.KnowledgeBase;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.repository.ChatbotRepository;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationTokenServiceTest {
    private IntegrationTokenRepository repository;
    private ChatbotRepository chatbotRepository;
    private IntegrationTokenService service;
    private UUID tenantId;
    private Chatbot chatbot;

    @BeforeEach
    void setUp() {
        repository = mock(IntegrationTokenRepository.class);
        chatbotRepository = mock(ChatbotRepository.class);
        service = new IntegrationTokenService(repository, chatbotRepository);
        ReflectionTestUtils.setField(service, "pepper", "test-pepper");

        tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(UUID.randomUUID());
        chatbot = new Chatbot();
        chatbot.setId(UUID.randomUUID());
        chatbot.setTenant(tenant);
        chatbot.setKnowledgeBase(knowledgeBase);
        when(chatbotRepository.findByTenantId(tenantId)).thenReturn(List.of(chatbot));
        when(repository.save(any(IntegrationToken.class))).thenAnswer(invocation -> {
            IntegrationToken token = invocation.getArgument(0);
            token.setId(UUID.randomUUID());
            token.setCreatedAt(LocalDateTime.now());
            return token;
        });
    }

    @Test
    void createReturnsSecretOnceAndPersistsOnlyHash() {
        var created = service.create(
                tenantId,
                new IntegrationTokenDtos.CreateRequest(
                        "Production widget",
                        List.of(IntegrationTokenService.WIDGET_SCOPE),
                        null
                )
        );

        assertTrue(created.secret().startsWith("ccn_it_"));
        ArgumentCaptor<IntegrationToken> captor = ArgumentCaptor.forClass(IntegrationToken.class);
        verify(repository).save(captor.capture());
        IntegrationToken stored = captor.getValue();
        assertNotEquals(created.secret(), stored.getTokenHash());
        assertFalse(stored.getTokenHash().contains(created.secret()));
        assertTrue(stored.getScopes().contains(IntegrationTokenService.WIDGET_SCOPE));
    }

    @Test
    void createRejectsUnknownScope() {
        assertThrows(BadRequestException.class, () -> service.create(
                tenantId,
                new IntegrationTokenDtos.CreateRequest("Bad token", List.of("tenant:admin"), null)
        ));
    }

    @Test
    void authenticateRejectsExpiredToken() {
        String secret = "ccn_it_expired";
        IntegrationToken token = new IntegrationToken();
        token.setTenant(chatbot.getTenant());
        token.setChatbot(chatbot);
        token.setScopes(List.of(IntegrationTokenService.API_SCOPE));
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByTokenHash(service.hash(secret))).thenReturn(Optional.of(token));

        assertThrows(UnauthorizedException.class, () -> service.authenticate(
                "Bearer " + secret,
                IntegrationTokenService.API_SCOPE
        ));
    }
}
