package com.cacanode.api.chat.controller;

import com.cacanode.api.chat.dto.ChatDtos;
import com.cacanode.api.chat.service.ChatControlPlaneService;
import com.cacanode.api.tenant.service.IntegrationTokenService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalChatControllerTest {
    @Test
    void widgetSubmissionPassesLocaleAndUsesWidgetAuthentication() {
        ChatControlPlaneService chatService = mock(ChatControlPlaneService.class);
        IntegrationTokenService tokenService = mock(IntegrationTokenService.class);
        ExternalChatController controller = new ExternalChatController(chatService, tokenService);
        UUID tenantId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        var principal = new IntegrationTokenService.Principal(
                tokenId, tenantId, UUID.randomUUID(), UUID.randomUUID(), List.of("widget:chat"));
        var body = new ChatDtos.WidgetSubmitMessageRequest(
                "Xin chào", Map.of("surface", "widget"), "vi-VN");
        var expected = new ChatDtos.AssistantMessageResponse(
                "assistant", "Chào bạn", List.of(), null);
        when(tokenService.authenticate("Bearer token", IntegrationTokenService.WIDGET_SCOPE,
                "https://example.com")).thenReturn(principal);
        when(chatService.submitWidgetMessage(tenantId, tokenId, sessionId, body.content(),
                body.metadata(), body.locale(), "operation-1", "request-1")).thenReturn(expected);

        var actual = controller.submitWidget(sessionId, body, "Bearer token",
                "https://example.com", "operation-1", "request-1");

        assertSame(expected, actual);
        verify(tokenService).authenticate("Bearer token", IntegrationTokenService.WIDGET_SCOPE,
                "https://example.com");
        verify(chatService).submitWidgetMessage(tenantId, tokenId, sessionId, body.content(),
                body.metadata(), "vi-VN", "operation-1", "request-1");
    }

    @Test
    void customApiSubmissionKeepsItsExistingRequestContract() {
        ChatControlPlaneService chatService = mock(ChatControlPlaneService.class);
        IntegrationTokenService tokenService = mock(IntegrationTokenService.class);
        ExternalChatController controller = new ExternalChatController(chatService, tokenService);
        UUID tenantId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        var principal = new IntegrationTokenService.Principal(
                tokenId, tenantId, UUID.randomUUID(), UUID.randomUUID(), List.of("api:chat"));
        var body = new ChatDtos.SubmitMessageRequest("Hello", Map.of("surface", "api"));
        var expected = new ChatDtos.AssistantMessageResponse(
                "assistant", "Hello", List.of(), null);
        when(tokenService.authenticate("Bearer token", IntegrationTokenService.API_SCOPE, null))
                .thenReturn(principal);
        when(chatService.submitExternalMessage(tenantId, tokenId, sessionId, body.content(),
                body.metadata(), "operation-2", "request-2")).thenReturn(expected);

        var actual = controller.submitApi(
                sessionId, body, "Bearer token", "operation-2", "request-2");

        assertSame(expected, actual);
        verify(tokenService).authenticate("Bearer token", IntegrationTokenService.API_SCOPE, null);
        verify(chatService).submitExternalMessage(tenantId, tokenId, sessionId, body.content(),
                body.metadata(), "operation-2", "request-2");
    }
}
