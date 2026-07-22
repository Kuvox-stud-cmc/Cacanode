package com.cacanode.api.tenant.service;

import com.cacanode.api.tenant.api.ApplyTenantEntitlementsCommand;
import com.cacanode.api.tenant.api.TenantEntitlements;
import com.cacanode.api.tenant.api.TenantEntitlementApi;
import com.cacanode.api.tenant.cache.IntegrationTokenCacheInvalidationPublisher;
import com.cacanode.api.tenant.dto.WidgetConfigDtos;
import com.cacanode.api.tenant.api.TenantPlan;
import com.cacanode.api.tenant.api.TenantStatus;
import com.cacanode.api.tenant.enums.WidgetPosition;
import com.cacanode.api.tenant.enums.WidgetIconStyle;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.UserRepository;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import com.cacanode.api.tenant.service.implement.TenantModuleApiImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationTokenMutationInvalidationTest {

    @Test
    void allowedOriginChangesInvalidateOnlyTheAffectedChatbotTokens() {
        UUID tenantId = UUID.randomUUID();
        UUID chatbotId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        Chatbot chatbot = new Chatbot();
        chatbot.setId(chatbotId);
        chatbot.setAllowedOrigins(List.of("https://old.example"));
        WidgetConfig config = new WidgetConfig();
        config.setTenant(tenant);
        config.setChatbot(chatbot);
        WidgetConfigRepository repository = mock(WidgetConfigRepository.class);
        when(repository.findFirstByTenant_IdOrderByCreatedAtAsc(tenantId))
                .thenReturn(Optional.of(config));
        TenantEntitlementApi tenantModuleApi = mock(TenantEntitlementApi.class);
        when(tenantModuleApi.getEntitlements(tenantId)).thenReturn(entitlements(tenantId, true));
        IntegrationTokenCacheInvalidationPublisher publisher =
                mock(IntegrationTokenCacheInvalidationPublisher.class);
        WidgetConfigService service = new WidgetConfigService(repository, tenantModuleApi, publisher);

        service.update(tenantId, new WidgetConfigDtos.UpdateRequest(
                "Assistant", "Hello", "#112233", WidgetPosition.BOTTOM_RIGHT,
                true, List.of("https://new.example"), false,
                WidgetIconStyle.GLOW
        ));

        assertEquals(WidgetIconStyle.GLOW, config.getIconStyle());
        verify(publisher).publishChatbotTokens(chatbotId);
    }

    @Test
    void unchangedAllowedOriginsDoNotInvalidate() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        Chatbot chatbot = new Chatbot();
        chatbot.setId(UUID.randomUUID());
        chatbot.setAllowedOrigins(List.of("https://same.example"));
        WidgetConfig config = new WidgetConfig();
        config.setTenant(tenant);
        config.setChatbot(chatbot);
        WidgetConfigRepository repository = mock(WidgetConfigRepository.class);
        when(repository.findFirstByTenant_IdOrderByCreatedAtAsc(tenantId))
                .thenReturn(Optional.of(config));
        TenantEntitlementApi tenantModuleApi = mock(TenantEntitlementApi.class);
        when(tenantModuleApi.getEntitlements(tenantId)).thenReturn(entitlements(tenantId, true));
        IntegrationTokenCacheInvalidationPublisher publisher =
                mock(IntegrationTokenCacheInvalidationPublisher.class);
        WidgetConfigService service = new WidgetConfigService(repository, tenantModuleApi, publisher);

        service.update(tenantId, new WidgetConfigDtos.UpdateRequest(
                "Assistant", "Hello", "#112233", WidgetPosition.BOTTOM_RIGHT,
                true, List.of("https://same.example"), false,
                WidgetIconStyle.STANDARD
        ));

        verify(publisher, never()).publishChatbotTokens(chatbot.getId());
    }

    @Test
    void entitlementChangesInvalidateTenantTokensOnlyWhenApiAccessChanges() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setApiAccessEnabled(true);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));
        IntegrationTokenCacheInvalidationPublisher publisher =
                mock(IntegrationTokenCacheInvalidationPublisher.class);
        TenantModuleApiImpl service = new TenantModuleApiImpl(
                mock(PasswordEncoder.class),
                tenantRepository,
                mock(UserRepository.class),
                mock(com.cacanode.api.tenant.repository.InvitationRepository.class),
                mock(TenantUserManagementService.class),
                mock(TenantWorkspaceService.class),
                mock(ApplicationEventPublisher.class),
                publisher
        );

        service.applyEntitlements(command(tenantId, true));
        verify(publisher, never()).publishTenantTokens(tenantId);

        service.applyEntitlements(command(tenantId, false));
        verify(publisher).publishTenantTokens(tenantId);
    }

    private static ApplyTenantEntitlementsCommand command(UUID tenantId, boolean apiAccess) {
        LocalDateTime now = LocalDateTime.now();
        return new ApplyTenantEntitlementsCommand(
                tenantId, TenantPlan.PRO, TenantStatus.ACTIVE, 100, 10, 3, 1024,
                now.plusDays(1), now, now.plusMonths(1), null,
                apiAccess, true, true, true
        );
    }

    private static TenantEntitlements entitlements(UUID tenantId, boolean apiAccess) {
        return new TenantEntitlements(
                tenantId, TenantPlan.PRO, TenantStatus.ACTIVE, 100, 10, 3, 1024,
                LocalDateTime.now(), null, null, apiAccess, true, true, true
        );
    }
}
