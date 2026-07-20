package com.cacanode.api.tenant.service;

import com.cacanode.api.common.event.TenantCreatedEvent;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.tenant.dto.IntegrationTokenDtos;
import com.cacanode.api.tenant.dto.WidgetEmbedDtos;
import com.cacanode.api.tenant.model.IntegrationToken;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManagedWidgetTokenService {
    private static final String TOKEN_NAME = "Website widget";

    private final WidgetConfigRepository widgetConfigRepository;
    private final IntegrationTokenRepository tokenRepository;
    private final IntegrationTokenService tokenService;
    private final IntegrationSecretCryptoService cryptoService;
    private final TenantRepository tenantRepository;

    @EventListener
    @Transactional
    public void provisionAfterTenantCreation(TenantCreatedEvent event) {
        ensure(event.tenantId());
    }

    @Transactional
    public WidgetEmbedDtos.Response getOrCreate(UUID tenantId) {
        WidgetConfig config = ensure(tenantId);
        IntegrationToken token = config.getManagedWidgetToken();
        IntegrationSecretCryptoService.DecryptedSecret secret =
                cryptoService.decryptForMigration(config.getEncryptedWidgetTokenSecret());
        if (secret.requiresReencryption()) {
            config.setEncryptedWidgetTokenSecret(cryptoService.encrypt(secret.value()));
            widgetConfigRepository.save(config);
        }
        tokenService.migrateHashIfRequired(token, secret.value());
        return new WidgetEmbedDtos.Response(
                token.getId(), token.getTokenPrefix(),
                secret.value()
        );
    }

    private WidgetConfig ensure(UUID tenantId) {
        tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant was not found"));
        WidgetConfig config = widgetConfigRepository.findFirstByTenant_IdOrderByCreatedAtAsc(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Widget configuration was not found"));
        if (isUsable(config)) {
            return config;
        }

        IntegrationTokenDtos.Created created = tokenService.create(tenantId, new IntegrationTokenDtos.CreateRequest(
                TOKEN_NAME, List.of(IntegrationTokenService.WIDGET_SCOPE), null));
        config.setManagedWidgetToken(tokenRepository.getReferenceById(created.token().id()));
        config.setEncryptedWidgetTokenSecret(cryptoService.encrypt(created.secret()));
        return widgetConfigRepository.save(config);
    }

    private boolean isUsable(WidgetConfig config) {
        IntegrationToken token = config.getManagedWidgetToken();
        if (token == null || config.getEncryptedWidgetTokenSecret() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return token.getRevokedAt() == null
                && (token.getExpiresAt() == null || token.getExpiresAt().isAfter(now))
                && token.getScopes().contains(IntegrationTokenService.WIDGET_SCOPE);
    }
}
