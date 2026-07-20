package com.cacanode.api.chat.controller;

import com.cacanode.api.chat.service.ChatControlPlaneService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatControllerTest {
    @Test
    void metadataHeadersAreReturnedWithoutChangingListBodies() {
        ChatControlPlaneService service = mock(ChatControlPlaneService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(request.getAttribute("tenantId")).thenReturn(tenantId.toString());
        when(request.getAttribute("userId")).thenReturn(userId.toString());
        when(service.playgroundPage(tenantId, userId, 30, 0, null, null, null,
                null, null, null, null))
                .thenReturn(new ChatControlPlaneService.PlaygroundPage(List.of(), "opaque-token"));
        when(service.conversationPage(tenantId, null, null, 20, 0, null,
                null, null, null, null))
                .thenReturn(new ChatControlPlaneService.ConversationPage(List.of(), 42));

        ChatController controller = new ChatController(service);
        var playground = controller.playgroundResponse(30, 0, null, null, null,
                null, null, null, null, request);
        var conversations = controller.conversationsResponse(null, null, 20, 0,
                null, null, null, null, null, request);

        assertEquals("opaque-token", playground.getHeaders().getFirst("X-Next-Cursor"));
        assertEquals(List.of(), playground.getBody());
        assertEquals("42", conversations.getHeaders().getFirst("X-Total-Count"));
        assertEquals(List.of(), conversations.getBody());
    }
}
