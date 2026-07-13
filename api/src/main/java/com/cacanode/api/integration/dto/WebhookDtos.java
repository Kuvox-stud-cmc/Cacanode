package com.cacanode.api.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class WebhookDtos {
    private WebhookDtos() {
    }

    public record UpsertRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank String url,
            @NotEmpty List<String> events,
            boolean active
    ) {
    }

    public record Response(
            UUID id,
            String name,
            String url,
            List<String> events,
            boolean active,
            LocalDateTime lastDeliveryAt,
            String lastDeliveryStatus,
            LocalDateTime createdAt
    ) {
    }

    public record Created(Response endpoint, String signingSecret) {
    }
}
