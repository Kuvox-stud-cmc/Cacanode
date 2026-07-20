package com.cacanode.api.tenant.service;

import com.cacanode.api.common.event.TenantCreatedEvent;
import com.cacanode.api.tenant.dto.IntegrationTokenDtos;
import com.cacanode.api.tenant.model.IntegrationToken;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedWidgetTokenServiceTest {
    private final WidgetConfigRepository widgetConfigRepository = mock(WidgetConfigRepository.class);
    private final IntegrationTokenRepository tokenRepository = mock(IntegrationTokenRepository.class);
    private final IntegrationTokenService tokenService = mock(IntegrationTokenService.class);
    private final IntegrationSecretCryptoService cryptoService = mock(IntegrationSecretCryptoService.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final ManagedWidgetTokenService service = new ManagedWidgetTokenService(
            widgetConfigRepository, tokenRepository, tokenService, cryptoService, tenantRepository);
    private final UUID tenantId = UUID.randomUUID();
    private final WidgetConfig config = new WidgetConfig();

    @BeforeEach
    void setUp() {
        when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.of(mock(com.cacanode.api.tenant.model.Tenant.class)));
        when(widgetConfigRepository.findFirstByTenant_IdOrderByCreatedAtAsc(tenantId))
                .thenReturn(Optional.of(config));
        when(widgetConfigRepository.save(config)).thenReturn(config);
    }

    @Test
    void createsAndEncryptsAutomaticWidgetToken() {
        UUID tokenId = UUID.randomUUID();
        String secret = "ccn_it_widget_secret";
        IntegrationToken token = token(tokenId);
        when(tokenService.create(any(), any())).thenReturn(new IntegrationTokenDtos.Created(
                new IntegrationTokenDtos.Item(tokenId, "Website widget", "ccn_it_widget", List.of("widget:chat"),
                        null, null, null, LocalDateTime.now()), secret));
        when(tokenRepository.getReferenceById(tokenId)).thenReturn(token);
        when(cryptoService.encrypt(secret)).thenReturn("encrypted-secret");
        when(cryptoService.decryptForMigration("encrypted-secret"))
                .thenReturn(new IntegrationSecretCryptoService.DecryptedSecret(secret, false));

        var response = service.getOrCreate(tenantId);

        assertEquals(tokenId, response.tokenId());
        assertEquals(secret, response.secret());
        assertEquals(token, config.getManagedWidgetToken());
        assertEquals("encrypted-secret", config.getEncryptedWidgetTokenSecret());
        verify(widgetConfigRepository).save(config);
        verify(tokenService).migrateHashIfRequired(token, secret);
    }

    @Test
    void reusesExistingActiveManagedToken() {
        IntegrationToken token = token(UUID.randomUUID());
        config.setManagedWidgetToken(token);
        config.setEncryptedWidgetTokenSecret("encrypted-secret");
        when(cryptoService.decryptForMigration("encrypted-secret"))
                .thenReturn(new IntegrationSecretCryptoService.DecryptedSecret("ccn_it_existing", false));

        var response = service.getOrCreate(tenantId);

        assertEquals(token.getId(), response.tokenId());
        assertEquals("ccn_it_existing", response.secret());
        verify(tokenService, never()).create(any(), any());
        verify(tokenService).migrateHashIfRequired(token, "ccn_it_existing");
    }

    @Test
    void reencryptsLegacyManagedTokenSecretWithoutRotatingToken() {
        IntegrationToken token = token(UUID.randomUUID());
        config.setManagedWidgetToken(token);
        config.setEncryptedWidgetTokenSecret("legacy-encrypted-secret");
        when(cryptoService.decryptForMigration("legacy-encrypted-secret"))
                .thenReturn(new IntegrationSecretCryptoService.DecryptedSecret("ccn_it_existing", true));
        when(cryptoService.encrypt("ccn_it_existing")).thenReturn("current-encrypted-secret");

        var response = service.getOrCreate(tenantId);

        assertEquals(token.getId(), response.tokenId());
        assertEquals("ccn_it_existing", response.secret());
        assertEquals("current-encrypted-secret", config.getEncryptedWidgetTokenSecret());
        verify(widgetConfigRepository).save(config);
        verify(tokenService, never()).create(any(), any());
        verify(tokenService).migrateHashIfRequired(token, "ccn_it_existing");
    }

    @Test
    void tenantCreationEventProvisionsTokenImmediately() {
        UUID tokenId = UUID.randomUUID();
        IntegrationToken token = token(tokenId);
        when(tokenService.create(any(), any())).thenReturn(new IntegrationTokenDtos.Created(
                new IntegrationTokenDtos.Item(tokenId, "Website widget", "ccn_it_widget", List.of("widget:chat"),
                        null, null, null, LocalDateTime.now()), "ccn_it_secret"));
        when(tokenRepository.getReferenceById(tokenId)).thenReturn(token);
        when(cryptoService.encrypt("ccn_it_secret")).thenReturn("encrypted-secret");

        service.provisionAfterTenantCreation(new TenantCreatedEvent(
                tenantId, UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now().plusDays(14)));

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
