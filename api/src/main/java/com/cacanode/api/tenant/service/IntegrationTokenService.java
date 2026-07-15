package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.tenant.dto.IntegrationTokenDtos;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.IntegrationToken;
import com.cacanode.api.tenant.repository.ChatbotRepository;
import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.cacanode.api.tenant.api.TenantModuleApi;

@Service
public class IntegrationTokenService {
    public static final String WIDGET_SCOPE = "widget:chat";
    public static final String API_SCOPE = "api:chat";
    private static final Set<String> ALLOWED_SCOPES = Set.of(WIDGET_SCOPE, API_SCOPE);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IntegrationTokenRepository repository;
    private final ChatbotRepository chatbotRepository;
    private final TenantModuleApi tenantModuleApi;

    @Autowired
    public IntegrationTokenService(
            IntegrationTokenRepository repository,
            ChatbotRepository chatbotRepository,
            TenantModuleApi tenantModuleApi
    ) {
        this.repository = repository;
        this.chatbotRepository = chatbotRepository;
        this.tenantModuleApi = tenantModuleApi;
    }

    public IntegrationTokenService(IntegrationTokenRepository repository, ChatbotRepository chatbotRepository) {
        this(repository, chatbotRepository, null);
    }

    @Value("${app.integrations.token-pepper:development-integration-token-pepper}")
    private String pepper;

    @Transactional(readOnly = true)
    public List<IntegrationTokenDtos.Item> list(UUID tenantId) {
        return repository.findByTenant_IdOrderByCreatedAtDesc(tenantId).stream().map(this::toItem).toList();
    }

    @Transactional
    public IntegrationTokenDtos.Created create(UUID tenantId, IntegrationTokenDtos.CreateRequest request) {
        List<String> scopes = request.scopes().stream().distinct().sorted().toList();
        if (scopes.isEmpty() || !ALLOWED_SCOPES.containsAll(scopes)) {
            throw new BadRequestException("Token scopes are invalid");
        }
        if (tenantModuleApi != null && scopes.contains(API_SCOPE)
                && !tenantModuleApi.getEntitlements(tenantId).apiAccess()) {
            throw new BadRequestException("API_ACCESS_REQUIRES_PRO");
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Token expiry must be in the future");
        }
        Chatbot chatbot = chatbotRepository.findByTenantId(tenantId).stream()
                .filter(item -> item.getStatus().name().equals("ACTIVE"))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Active chatbot was not found"));

        String secret = "ccn_it_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        IntegrationToken token = new IntegrationToken();
        token.setTenant(chatbot.getTenant());
        token.setChatbot(chatbot);
        token.setName(request.name().trim());
        token.setTokenPrefix(secret.substring(0, Math.min(secret.length(), 16)));
        token.setTokenHash(hash(secret));
        token.setScopes(scopes);
        token.setExpiresAt(request.expiresAt());
        token = repository.save(token);
        return new IntegrationTokenDtos.Created(toItem(token), secret);
    }

    @Transactional
    public void revoke(UUID tenantId, UUID tokenId) {
        IntegrationToken token = repository.findByIdAndTenant_Id(tokenId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Integration token was not found"));
        token.setRevokedAt(LocalDateTime.now());
    }

    @Transactional
    public IntegrationTokenDtos.Created rotate(UUID tenantId, UUID tokenId) {
        IntegrationToken token = repository.findByIdAndTenant_Id(tokenId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Integration token was not found"));
        token.setRevokedAt(LocalDateTime.now());
        LocalDateTime expiresAt = token.getExpiresAt() != null
                && token.getExpiresAt().isAfter(LocalDateTime.now())
                ? token.getExpiresAt() : null;
        return create(tenantId, new IntegrationTokenDtos.CreateRequest(
                token.getName(), List.copyOf(token.getScopes()), expiresAt
        ));
    }

    @Transactional
    public Principal authenticate(String authorization, String requiredScope) {
        String secret = bearerSecret(authorization);
        IntegrationToken token = repository.findByTokenHash(hash(secret))
                .orElseThrow(() -> new UnauthorizedException("Integration token is invalid"));
        LocalDateTime now = LocalDateTime.now();
        if (token.getRevokedAt() != null || (token.getExpiresAt() != null && !token.getExpiresAt().isAfter(now))) {
            throw new UnauthorizedException("Integration token is expired or revoked");
        }
        if (!token.getScopes().contains(requiredScope)) {
            throw new UnauthorizedException("Integration token does not have the required scope");
        }
        if (tenantModuleApi != null && API_SCOPE.equals(requiredScope)
                && !tenantModuleApi.getEntitlements(token.getTenant().getId()).apiAccess()) {
            throw new UnauthorizedException("API access is disabled for this tenant");
        }
        token.setLastUsedAt(now);
        return new Principal(
                token.getId(),
                token.getTenant().getId(),
                token.getChatbot().getId(),
                token.getChatbot().getKnowledgeBase().getId(),
                List.copyOf(token.getScopes())
        );
    }

    @Transactional
    public Principal authenticateForAnyChatScope(String authorization) {
        String secret = bearerSecret(authorization);
        IntegrationToken token = repository.findByTokenHash(hash(secret))
                .orElseThrow(() -> new UnauthorizedException("Integration token is invalid"));
        LocalDateTime now = LocalDateTime.now();
        if (token.getRevokedAt() != null || (token.getExpiresAt() != null && !token.getExpiresAt().isAfter(now))) {
            throw new UnauthorizedException("Integration token is expired or revoked");
        }
        if (!token.getScopes().contains(WIDGET_SCOPE) && !token.getScopes().contains(API_SCOPE)) {
            throw new UnauthorizedException("Integration token does not have chat access");
        }
        token.setLastUsedAt(now);
        return new Principal(
                token.getId(), token.getTenant().getId(), token.getChatbot().getId(),
                token.getChatbot().getKnowledgeBase().getId(), List.copyOf(token.getScopes())
        );
    }

    public String hash(String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to hash integration token", e);
        }
    }

    private String bearerSecret(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ccn_it_")) {
            throw new UnauthorizedException("Integration token is required");
        }
        return authorization.substring("Bearer ".length());
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private IntegrationTokenDtos.Item toItem(IntegrationToken token) {
        return new IntegrationTokenDtos.Item(
                token.getId(), token.getName(), token.getTokenPrefix(), List.copyOf(token.getScopes()),
                token.getExpiresAt(), token.getLastUsedAt(), token.getRevokedAt(), token.getCreatedAt()
        );
    }

    public record Principal(
            UUID tokenId,
            UUID tenantId,
            UUID chatbotId,
            UUID knowledgeBaseId,
            List<String> scopes
    ) {
    }
}
