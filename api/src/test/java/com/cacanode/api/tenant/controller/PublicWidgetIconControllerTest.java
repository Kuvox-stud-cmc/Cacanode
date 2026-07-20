package com.cacanode.api.tenant.controller;

import com.cacanode.api.common.exception.custom.UnauthorizedException;
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

class PublicWidgetIconControllerTest {
    private final IntegrationTokenService tokenService = mock(IntegrationTokenService.class);
    private final WidgetConfigService configService = mock(WidgetConfigService.class);
    private final WidgetIconService iconService = mock(WidgetIconService.class);
    private final PublicWidgetController controller =
            new PublicWidgetController(tokenService, configService, iconService);
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void authenticatedAllowedOriginReceivesPrivateNoStoreIcon() {
        authorize(List.of("https://example.com"));
        byte[] content = new byte[]{1, 2, 3};
        when(iconService.load(tenantId)).thenReturn(new WidgetIconService.WidgetIcon(content, "image/webp"));

        var response = controller.icon("Bearer ccn_it_secret", "https://example.com");

        assertEquals("image/webp", response.getHeaders().getContentType().toString());
        assertEquals(3, response.getHeaders().getContentLength());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertArrayEquals(content, response.getBody());
        verify(iconService).load(tenantId);
    }

    @Test
    void disallowedOriginCannotLoadIcon() {
        authorize(List.of("https://allowed.example"));

        assertThrows(UnauthorizedException.class,
                () -> controller.icon("Bearer ccn_it_secret", "https://blocked.example"));
    }

    private void authorize(List<String> origins) {
        var principal = new IntegrationTokenService.Principal(
                UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(), List.of("widget:chat"));
        when(tokenService.authenticate("Bearer ccn_it_secret", IntegrationTokenService.WIDGET_SCOPE))
                .thenReturn(principal);
        when(configService.get(tenantId)).thenReturn(new WidgetConfigDtos.Response(
                principal.chatbotId(), "Assistant", "Hello", "#4f46e5", WidgetPosition.BOTTOM_RIGHT,
                true, origins, false, true, "/api/v1/public/widget/icon",
                com.cacanode.api.tenant.enums.WidgetIconStyle.GLOW));
    }
}
