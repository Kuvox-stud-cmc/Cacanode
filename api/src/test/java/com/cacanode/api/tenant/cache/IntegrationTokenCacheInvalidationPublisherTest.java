package com.cacanode.api.tenant.cache;

import com.cacanode.api.tenant.repository.IntegrationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationTokenCacheInvalidationPublisherTest {

    private IntegrationTokenRepository repository;
    private ApplicationEventPublisher eventPublisher;
    private IntegrationTokenCacheInvalidationPublisher publisher;

    @BeforeEach
    void setUp() {
        repository = mock(IntegrationTokenRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        publisher = new IntegrationTokenCacheInvalidationPublisher(repository, eventPublisher);
    }

    @Test
    void resolvesOnlyChatbotTokenHashesInsideTheCallerTransaction() {
        UUID chatbotId = UUID.randomUUID();
        IntegrationTokenRepository.TokenHashProjection first = projection("hash-a");
        IntegrationTokenRepository.TokenHashProjection second = projection("hash-b");
        when(repository.findTokenHashesByChatbotId(chatbotId)).thenReturn(List.of(first, second));

        publisher.publishChatbotTokens(chatbotId);

        ArgumentCaptor<IntegrationTokenCacheInvalidationEvent> event =
                ArgumentCaptor.forClass(IntegrationTokenCacheInvalidationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(java.util.Set.of("hash-a", "hash-b"), event.getValue().tokenHashes());
    }

    @Test
    void doesNotPublishAnEmptyTenantInvalidation() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findTokenHashesByTenantId(tenantId)).thenReturn(List.of());

        publisher.publishTenantTokens(tenantId);

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private IntegrationTokenRepository.TokenHashProjection projection(String hash) {
        IntegrationTokenRepository.TokenHashProjection projection =
                mock(IntegrationTokenRepository.TokenHashProjection.class);
        when(projection.getTokenHash()).thenReturn(hash);
        return projection;
    }
}
