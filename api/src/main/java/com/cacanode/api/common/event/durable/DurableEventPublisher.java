package com.cacanode.api.common.event.durable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DurableEventPublisher {
    private final ModuleEventOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public UUID publish(String stableType, int version, Object payload) {
        try {
            LocalDateTime now = LocalDateTime.now();
            ModuleEventOutbox event = new ModuleEventOutbox();
            event.setEventId(UUID.randomUUID());
            event.setEventType(stableType);
            event.setEventVersion(version);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setCreatedAt(now);
            event.setStatus(ModuleEventStatus.PENDING);
            event.setAttempts(0);
            event.setNextAttemptAt(now);
            repository.save(event);
            return event.getEventId();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Module event payload cannot be serialized", exception);
        }
    }
}
