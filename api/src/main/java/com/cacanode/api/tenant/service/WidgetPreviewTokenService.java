package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.tenant.enums.ChatbotStatus;
import com.cacanode.api.tenant.enums.KnowledgeBaseStatus;
import com.cacanode.api.tenant.enums.TenantStatus;
import com.cacanode.api.tenant.model.IntegrationToken;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class WidgetPreviewTokenService {
    public static final String PREFIX = "ccn_wp_";
    private static final String SIGNATURE_PURPOSE = "cacanode-widget-preview-v1:";

    private final IntegrationTokenRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${app.widget-preview.signing-key:development-widget-preview-signing-key}")
    private String signingKey;

    @Value("${app.widget-preview.ttl-seconds:900}")
    private long ttlSeconds;

    public WidgetPreviewTokenService(IntegrationTokenRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public String issue(UUID tenantId, UUID integrationTokenId) {
        PreviewPayload payload = new PreviewPayload(
                tenantId, integrationTokenId, Instant.now().plusSeconds(ttlSeconds).getEpochSecond());
        String encodedPayload = encode(write(payload));
        return PREFIX + encodedPayload + "." + encode(sign(encodedPayload));
    }

    @Transactional
    public IntegrationTokenService.Principal authenticate(String secret) {
        PreviewPayload payload = verify(secret);
        IntegrationToken token = repository.findWithContextById(payload.integrationTokenId())
                .orElseThrow(this::invalid);
        LocalDateTime now = LocalDateTime.now();
        if (!payload.tenantId().equals(token.getTenant().getId())
                || token.getRevokedAt() != null
                || token.getExpiresAt() != null && !token.getExpiresAt().isAfter(now)
                || !token.getScopes().contains(IntegrationTokenService.WIDGET_SCOPE)
                || token.getTenant().getStatus() != TenantStatus.ACTIVE
                        && token.getTenant().getStatus() != TenantStatus.TRIAL
                || token.getChatbot().getStatus() != ChatbotStatus.ACTIVE
                || token.getChatbot().getKnowledgeBase().getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw invalid();
        }
        token.setLastUsedAt(now);
        return new IntegrationTokenService.Principal(
                token.getId(), token.getTenant().getId(), token.getChatbot().getId(),
                token.getChatbot().getKnowledgeBase().getId(), List.copyOf(token.getScopes()));
    }

    private PreviewPayload verify(String secret) {
        try {
            if (secret == null || !secret.startsWith(PREFIX)) {
                throw invalid();
            }
            String[] parts = secret.substring(PREFIX.length()).split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(
                    sign(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) {
                throw invalid();
            }
            PreviewPayload payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]), PreviewPayload.class);
            if (payload.expiresAt() <= Instant.now().getEpochSecond()) {
                throw invalid();
            }
            return payload;
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (RuntimeException | java.io.IOException exception) {
            throw invalid();
        }
    }

    private byte[] write(PreviewPayload payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to encode widget preview token", exception);
        }
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal((SIGNATURE_PURPOSE + encodedPayload).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign widget preview token", exception);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private UnauthorizedException invalid() {
        return new UnauthorizedException("Widget preview token is invalid or expired");
    }

    private record PreviewPayload(UUID tenantId, UUID integrationTokenId, long expiresAt) {
    }
}
