package com.cacanode.api.tenant.cache;

import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class IntegrationTokenCacheInvalidationPublisher {

    private final IntegrationTokenRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public void publishTokenHash(String tokenHash) {
        publish(Set.of(tokenHash));
    }

    public void publishChatbotTokens(UUID chatbotId) {
        publish(repository.findTokenHashesByChatbotId(chatbotId).stream()
                .map(IntegrationTokenRepository.TokenHashProjection::getTokenHash)
                .collect(Collectors.toUnmodifiableSet()));
    }

    public void publishTenantTokens(UUID tenantId) {
        publish(repository.findTokenHashesByTenantId(tenantId).stream()
                .map(IntegrationTokenRepository.TokenHashProjection::getTokenHash)
                .collect(Collectors.toUnmodifiableSet()));
    }

    private void publish(Set<String> tokenHashes) {
        if (!tokenHashes.isEmpty()) {
            eventPublisher.publishEvent(new IntegrationTokenCacheInvalidationEvent(tokenHashes));
        }
    }
}
