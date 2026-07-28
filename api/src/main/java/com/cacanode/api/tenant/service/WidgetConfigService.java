package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.common.cache.BusinessCache;
import com.cacanode.api.common.cache.BusinessCacheInvalidationPublisher;
import com.cacanode.api.common.cache.CacheKeyFactory;
import com.cacanode.api.common.cache.VersionedJsonCache;
import com.cacanode.api.tenant.api.TenantEntitlementApi;
import com.cacanode.api.tenant.cache.IntegrationTokenCacheInvalidationPublisher;
import com.cacanode.api.tenant.dto.WidgetConfigDtos;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.enums.WidgetIconStyle;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WidgetConfigService {
    private final WidgetConfigRepository repository;
    private final TenantEntitlementApi tenantModuleApi;
    private final IntegrationTokenCacheInvalidationPublisher cacheInvalidationPublisher;
    @Autowired(required = false)
    private VersionedJsonCache businessCache;
    @Autowired(required = false)
    private CacheKeyFactory cacheKeyFactory;
    @Autowired(required = false)
    private BusinessCacheInvalidationPublisher businessInvalidationPublisher;

    @Transactional(readOnly = true)
    public WidgetConfigDtos.Response get(UUID tenantId) {
        if (businessCache == null || cacheKeyFactory == null) {
            return loadAuthoritative(tenantId);
        }
        return businessCache.getOrLoad(
                BusinessCache.WIDGET_CONFIG,
                cacheKeyFactory.build("widget-config", "tenant", tenantId.toString()),
                WidgetConfigDtos.Response.class,
                () -> loadAuthoritative(tenantId)
        );
    }

    @Transactional
    public WidgetConfigDtos.Response update(UUID tenantId, WidgetConfigDtos.UpdateRequest request) {
        WidgetConfig config = find(tenantId);
        List<String> origins = request.allowedOrigins() == null ? List.of() : request.allowedOrigins().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .map(this::validateOrigin)
                .toList();
        config.setDisplayName(request.displayName().trim());
        config.setWelcomeMessage(request.welcomeMessage().trim());
        config.setPrimaryColor(request.primaryColor());
        config.setPosition(request.position());
        config.setActive(request.active());
        if (request.hideCacanodeBranding() != null) {
            config.setHideCacanodeBranding(request.hideCacanodeBranding());
        }
        if (request.iconStyle() != null) {
            config.setIconStyle(request.iconStyle());
        } else if (config.getIconStyle() == null) {
            config.setIconStyle(WidgetIconStyle.STANDARD);
        }
        if (!config.getChatbot().getAllowedOrigins().equals(origins)) {
            config.getChatbot().setAllowedOrigins(origins);
            cacheInvalidationPublisher.publishChatbotTokens(config.getChatbot().getId());
        }
        if (businessInvalidationPublisher != null) {
            businessInvalidationPublisher.widget(tenantId);
        }
        return toResponse(config);
    }

    private WidgetConfigDtos.Response loadAuthoritative(UUID tenantId) {
        return toResponse(find(tenantId));
    }

    WidgetConfig find(UUID tenantId) {
        return repository.findFirstByTenant_IdOrderByCreatedAtAsc(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Widget configuration was not found"));
    }

    private String validateOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getScheme() == null || uri.getPath() != null && !uri.getPath().isBlank()) {
                throw new IllegalArgumentException();
            }
            if (!uri.getScheme().equals("http") && !uri.getScheme().equals("https")) {
                throw new IllegalArgumentException();
            }
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (RuntimeException e) {
            throw new BadRequestException("Allowed origins must be valid http or https origins");
        }
    }

    WidgetConfigDtos.Response toResponse(WidgetConfig config) {
        return new WidgetConfigDtos.Response(
                config.getChatbot().getId(), config.getDisplayName(), config.getWelcomeMessage(),
                config.getPrimaryColor(), config.getPosition(), config.isActive(),
                List.copyOf(config.getChatbot().getAllowedOrigins()), config.isHideCacanodeBranding(),
                !tenantModuleApi.getEntitlements(config.getTenant().getId()).customBranding()
                        || !config.isHideCacanodeBranding(),
                config.getIconObjectKey() == null ? null : "/api/v1/public/widget/icon",
                config.getIconStyle() == null ? WidgetIconStyle.STANDARD : config.getIconStyle()
        );
    }
}
