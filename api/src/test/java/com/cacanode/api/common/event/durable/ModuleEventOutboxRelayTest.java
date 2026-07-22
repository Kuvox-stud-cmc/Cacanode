package com.cacanode.api.common.event.durable;

import com.cacanode.api.billing.api.event.QuotaWarningEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ModuleEventOutboxRelayTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void marksPublishedOnlyAfterSynchronousListenersReturn() throws Exception {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ModuleEventOutbox event = event();
        ModuleEventOutboxRelay relay = new ModuleEventOutboxRelay(
                mock(ModuleEventOutboxRepository.class),
                (type, version) -> QuotaWarningEvent.class,
                objectMapper, publisher);

        relay.deliver(event);

        verify(publisher).publishEvent(any(QuotaWarningEvent.class));
        assertEquals(ModuleEventStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
    }

    @Test
    void retriesWithBackoffAndDeadLettersAfterBoundedAttempts() throws Exception {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        doThrow(new IllegalStateException("consumer failed")).when(publisher).publishEvent(any(Object.class));
        ModuleEventOutbox event = event();
        ModuleEventOutboxRelay relay = new ModuleEventOutboxRelay(
                mock(ModuleEventOutboxRepository.class),
                (type, version) -> QuotaWarningEvent.class,
                objectMapper, publisher);
        event.setAttempts(9);
        relay.deliver(event);

        assertEquals(10, event.getAttempts());
        assertEquals(ModuleEventStatus.DEAD, event.getStatus());
        assertEquals("consumer failed", event.getLastError());
    }

    private ModuleEventOutbox event() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        ModuleEventOutbox event = new ModuleEventOutbox();
        event.setEventId(UUID.randomUUID());
        event.setEventType("billing.quota.warning.v1");
        event.setEventVersion(1);
        event.setPayload(objectMapper.writeValueAsString(
                new QuotaWarningEvent(UUID.randomUUID(), 8, 10)));
        event.setStatus(ModuleEventStatus.PENDING);
        event.setCreatedAt(now);
        event.setNextAttemptAt(now);
        return event;
    }
}
