package com.cacanode.api.tenant.controller;

import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.tenant.api.WidgetOriginNotAllowedException;
import com.cacanode.api.tenant.dto.WidgetConfigDtos;
import com.cacanode.api.tenant.enums.WidgetPosition;
import com.cacanode.api.tenant.service.IntegrationTokenService;
import com.cacanode.api.tenant.service.WidgetConfigService;
import com.cacanode.api.tenant.service.WidgetIconService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicWidgetControllerTest {
    private static final String AUTHORIZATION = "Bearer ccn_it_secret";
    private static final String ORIGIN = "https://example.com";

    private final IntegrationTokenService tokenService = mock(IntegrationTokenService.class);
    private final WidgetConfigService configService = mock(WidgetConfigService.class);
    private final WidgetIconService iconService = mock(WidgetIconService.class);
    private final PublicWidgetController controller =
            new PublicWidgetController(tokenService, configService, iconService);
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void authenticatedAllowedOriginReceivesPrivateNoStoreIcon() {
        authorize(true);
        byte[] content = new byte[]{1, 2, 3};
        when(iconService.load(tenantId)).thenReturn(new WidgetIconService.WidgetIcon(content, "image/webp"));

        var response = controller.icon(AUTHORIZATION, ORIGIN);

        assertEquals("image/webp", response.getHeaders().getContentType().toString());
        assertEquals(3, response.getHeaders().getContentLength());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertArrayEquals(content, response.getBody());
        verify(iconService).load(tenantId);
    }

    @Test
    void requestingBrowserOriginIsForwardedToTokenAuthentication() {
        authorize(true);

        controller.config(AUTHORIZATION, ORIGIN);

        verify(tokenService).authenticate(AUTHORIZATION, IntegrationTokenService.WIDGET_SCOPE, ORIGIN);
    }

    @Test
    void configIsReturnedForAnAllowedOrigin() {
        authorize(true);

        assertEquals("Assistant", controller.config(AUTHORIZATION, ORIGIN).displayName());
    }

    @Test
    void disallowedOriginCannotReadConfig() {
        denyOrigin();

        assertThrows(WidgetOriginNotAllowedException.class,
                () -> controller.config(AUTHORIZATION, "https://blocked.example"));
    }

    @Test
    void disallowedOriginCannotLoadIcon() {
        denyOrigin();

        assertThrows(WidgetOriginNotAllowedException.class,
                () -> controller.icon(AUTHORIZATION, "https://blocked.example"));
    }

    @Test
    void inactiveWidgetIsRejected() {
        authorize(false);

        assertThrows(UnauthorizedException.class, () -> controller.config(AUTHORIZATION, ORIGIN));
    }

    private void authorize(boolean active) {
        var principal = new IntegrationTokenService.Principal(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(), List.of("widget:chat"));
        when(tokenService.authenticate(AUTHORIZATION, IntegrationTokenService.WIDGET_SCOPE, ORIGIN))
                .thenReturn(principal);
        when(configService.get(tenantId)).thenReturn(new WidgetConfigDtos.Response(
                principal.chatbotId(), "Assistant", "Hello", "#4f46e5", WidgetPosition.BOTTOM_RIGHT,
                active, List.of(ORIGIN), false, true, "/api/v1/public/widget/icon",
                com.cacanode.api.tenant.enums.WidgetIconStyle.GLOW));
    }

    private void denyOrigin() {
        when(tokenService.authenticate(
                AUTHORIZATION, IntegrationTokenService.WIDGET_SCOPE, "https://blocked.example"))
                .thenThrow(new WidgetOriginNotAllowedException());
    }
}
