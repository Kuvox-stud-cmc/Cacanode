package com.cacanode.api.tenant.service;

import com.cacanode.api.tenant.dto.IntegrationTokenDtos;
import com.cacanode.api.common.cache.BusinessCacheInvalidationPublisher;
import com.cacanode.api.tenant.model.IntegrationToken;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedWidgetTokenServiceTest {
    private final WidgetConfigRepository widgetConfigRepository = mock(WidgetConfigRepository.class);
    private final IntegrationTokenRepository tokenRepository = mock(IntegrationTokenRepository.class);
    private final IntegrationTokenService tokenService = mock(IntegrationTokenService.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final BusinessCacheInvalidationPublisher cacheInvalidationPublisher =
            mock(BusinessCacheInvalidationPublisher.class);
    private final WidgetPreviewTokenService previewTokenService = mock(WidgetPreviewTokenService.class);
    private final ManagedWidgetTokenService service = new ManagedWidgetTokenService(
            widgetConfigRepository, tokenRepository, tokenService, tenantRepository, previewTokenService);
    private final UUID tenantId = UUID.randomUUID();
    private final WidgetConfig config = new WidgetConfig();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "businessInvalidationPublisher", cacheInvalidationPublisher);
        when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.of(mock(com.cacanode.api.tenant.model.Tenant.class)));
        when(widgetConfigRepository.findFirstByTenant_IdOrderByCreatedAtAsc(tenantId))
                .thenReturn(Optional.of(config));
        when(widgetConfigRepository.save(config)).thenReturn(config);
        when(previewTokenService.issue(any(), any())).thenReturn("ccn_wp_preview");
    }

    @Test
    void generatesWidgetTokenAndReturnsSecretOnlyFromMutation() {
        UUID tokenId = UUID.randomUUID();
        String secret = "ccn_it_widget_secret";
        IntegrationToken token = token(tokenId);
        when(tokenService.create(any(), any())).thenReturn(new IntegrationTokenDtos.Created(
                new IntegrationTokenDtos.Item(tokenId, "Website widget", "ccn_it_widget", List.of("widget:chat"),
                        null, null, null, LocalDateTime.now()), secret));
        when(tokenRepository.getReferenceById(tokenId)).thenReturn(token);

        var response = service.generate(tenantId);

        assertEquals(tokenId, response.tokenId());
        assertEquals(secret, response.secret());
        assertEquals("ccn_wp_preview", response.previewToken());
        assertEquals(token, config.getManagedWidgetToken());
        assertEquals(null, config.getEncryptedWidgetTokenSecret());
        assertEquals(true, config.isActive());
        verify(widgetConfigRepository).save(config);
        verify(cacheInvalidationPublisher).widget(tenantId);
    }

    @Test
    void readReturnsStatusWithoutReturningOrCreatingASecret() {
        IntegrationToken token = token(UUID.randomUUID());
        config.setManagedWidgetToken(token);

        var response = service.get(tenantId);

        assertEquals(token.getId(), response.tokenId());
        assertEquals(token.getTokenPrefix(), response.tokenPrefix());
        assertEquals(true, response.configured());
        assertEquals("ccn_wp_preview", response.previewToken());
    }

    @Test
    void readDoesNotSilentlyCreateADeletedWidgetToken() {
        var response = service.get(tenantId);

        assertEquals(null, response.tokenId());
        assertEquals(null, response.tokenPrefix());
        assertEquals(false, response.configured());
        assertEquals(null, response.previewToken());
    }

    @Test
    void regenerationRevokesPreviousManagedTokenBeforeAttachingReplacement() {
        UUID oldTokenId = UUID.randomUUID();
        config.setManagedWidgetToken(token(oldTokenId));
        UUID tokenId = UUID.randomUUID();
        IntegrationToken token = token(tokenId);
        when(tokenService.create(any(), any())).thenReturn(new IntegrationTokenDtos.Created(
                new IntegrationTokenDtos.Item(tokenId, "Website widget", "ccn_it_widget", List.of("widget:chat"),
                        null, null, null, LocalDateTime.now()), "ccn_it_secret"));
        when(tokenRepository.getReferenceById(tokenId)).thenReturn(token);

        service.generate(tenantId);

        verify(tokenService).revoke(tenantId, oldTokenId);
        verify(tokenService).create(any(), any());
        verify(widgetConfigRepository).save(config);
    }

    private static IntegrationToken token(UUID tokenId) {
        IntegrationToken token = new IntegrationToken();
        token.setId(tokenId);
        token.setTokenPrefix("ccn_it_widget");
        token.setScopes(List.of(IntegrationTokenService.WIDGET_SCOPE));
        return token;
    }
}
