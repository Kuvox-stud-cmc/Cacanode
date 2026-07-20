package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.common.cache.BusinessCacheInvalidationPublisher;
import com.cacanode.api.tenant.dto.IntegrationTokenDtos;
import com.cacanode.api.tenant.dto.WidgetEmbedDtos;
import com.cacanode.api.tenant.model.IntegrationToken;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManagedWidgetTokenService {
    private static final String TOKEN_NAME = "Website widget";

    private final WidgetConfigRepository widgetConfigRepository;
    private final IntegrationTokenRepository tokenRepository;
    private final IntegrationTokenService tokenService;
    private final TenantRepository tenantRepository;
    private final WidgetPreviewTokenService previewTokenService;
    @Autowired(required = false)
    private BusinessCacheInvalidationPublisher businessInvalidationPublisher;

    @Transactional(readOnly = true)
    public WidgetEmbedDtos.Response get(UUID tenantId) {
        WidgetConfig config = widgetConfigRepository.findFirstByTenant_IdOrderByCreatedAtAsc(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Widget configuration was not found"));
        IntegrationToken token = config.getManagedWidgetToken();
        boolean configured = token != null && token.getRevokedAt() == null
                && token.getScopes().contains(IntegrationTokenService.WIDGET_SCOPE);
        return new WidgetEmbedDtos.Response(
                configured ? token.getId() : null,
                configured ? token.getTokenPrefix() : null,
                configured,
                configured ? previewTokenService.issue(tenantId, token.getId()) : null
        );
    }

    @Transactional
    public WidgetEmbedDtos.Generated generate(UUID tenantId) {
        tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant was not found"));
        WidgetConfig config = widgetConfigRepository.findFirstByTenant_IdOrderByCreatedAtAsc(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Widget configuration was not found"));
        if (config.getManagedWidgetToken() != null) {
            tokenService.revoke(tenantId, config.getManagedWidgetToken().getId());
        }

        IntegrationTokenDtos.Created created = tokenService.create(tenantId, new IntegrationTokenDtos.CreateRequest(
                TOKEN_NAME, List.of(IntegrationTokenService.WIDGET_SCOPE), null));
        config.setManagedWidgetToken(tokenRepository.getReferenceById(created.token().id()));
        config.setEncryptedWidgetTokenSecret(null);
        config.setActive(true);
        widgetConfigRepository.save(config);
        if (businessInvalidationPublisher != null) {
            businessInvalidationPublisher.widget(tenantId);
        }
        return new WidgetEmbedDtos.Generated(
                created.token().id(), created.token().tokenPrefix(), created.secret(),
                previewTokenService.issue(tenantId, created.token().id()));
    }
}
