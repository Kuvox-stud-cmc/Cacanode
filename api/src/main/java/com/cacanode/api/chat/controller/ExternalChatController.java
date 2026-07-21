package com.cacanode.api.chat.controller;

import com.cacanode.api.chat.dto.ChatDtos;
import com.cacanode.api.chat.enums.ChatChannel;
import com.cacanode.api.chat.service.ChatControlPlaneService;
import com.cacanode.api.tenant.service.IntegrationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ExternalChatController {
    private final ChatControlPlaneService chatService;
    private final IntegrationTokenService tokenService;

    @PostMapping("/api/v1/widget/chat/sessions")
    public ChatDtos.SessionResponse createWidget(
            @Valid @RequestBody ChatDtos.ExternalCreateSessionRequest body,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Parent-Origin", required = false) String parentOrigin) {
        var principal = tokenService.authenticate(
                authorization, IntegrationTokenService.WIDGET_SCOPE, parentOrigin);
        return chatService.createExternalSession(
                principal.tenantId(), principal.chatbotId(), principal.knowledgeBaseId(),
                principal.tokenId(), ChatChannel.WIDGET, body);
    }

    @PostMapping("/api/v1/external/chat/sessions")
    public ChatDtos.SessionResponse createApi(
            @Valid @RequestBody ChatDtos.ExternalCreateSessionRequest body,
            @RequestHeader("Authorization") String authorization) {
        var principal = tokenService.authenticate(authorization, IntegrationTokenService.API_SCOPE, null);
        return chatService.createExternalSession(
                principal.tenantId(), principal.chatbotId(), principal.knowledgeBaseId(),
                principal.tokenId(), ChatChannel.CUSTOM_API, body);
    }

    @PostMapping("/api/v1/widget/chat/sessions/{sessionId}/messages")
    public ChatDtos.AssistantMessageResponse submitWidget(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatDtos.WidgetSubmitMessageRequest body,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Parent-Origin", required = false) String parentOrigin,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        var principal = tokenService.authenticate(
                authorization, IntegrationTokenService.WIDGET_SCOPE, parentOrigin);
        return chatService.submitWidgetMessage(
                principal.tenantId(), principal.tokenId(), sessionId, body.content(),
                body.metadata(), body.locale(), idempotencyKey, requestId);
    }

    @PostMapping("/api/v1/external/chat/sessions/{sessionId}/messages")
    public ChatDtos.AssistantMessageResponse submitApi(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatDtos.SubmitMessageRequest body,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        var principal = tokenService.authenticate(
                authorization, IntegrationTokenService.API_SCOPE, null);
        return chatService.submitExternalMessage(
                principal.tenantId(), principal.tokenId(), sessionId, body.content(),
                body.metadata(), idempotencyKey, requestId);
    }

    @GetMapping({
            "/api/v1/widget/chat/sessions/{sessionId}/messages",
            "/api/v1/external/chat/sessions/{sessionId}/messages"
    })
    public List<ChatDtos.MessageResponse> history(
            @PathVariable UUID sessionId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Parent-Origin", required = false) String parentOrigin,
            HttpServletRequest request) {
        String scope = request.getRequestURI().contains("/widget/")
                ? IntegrationTokenService.WIDGET_SCOPE : IntegrationTokenService.API_SCOPE;
        var principal = tokenService.authenticate(authorization, scope, parentOrigin);
        return chatService.history(
                principal.tenantId(), null, principal.tokenId(), sessionId, 50, 0);
    }

    @DeleteMapping({
            "/api/v1/widget/chat/sessions/{sessionId}",
            "/api/v1/external/chat/sessions/{sessionId}"
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(
            @PathVariable UUID sessionId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Parent-Origin", required = false) String parentOrigin,
            HttpServletRequest request) {
        String scope = request.getRequestURI().contains("/widget/")
                ? IntegrationTokenService.WIDGET_SCOPE : IntegrationTokenService.API_SCOPE;
        var principal = tokenService.authenticate(authorization, scope, parentOrigin);
        chatService.close(principal.tenantId(), null, principal.tokenId(), sessionId);
    }
}
