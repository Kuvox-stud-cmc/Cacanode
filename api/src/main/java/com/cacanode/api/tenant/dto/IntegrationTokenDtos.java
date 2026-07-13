package com.cacanode.api.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class IntegrationTokenDtos {
    private IntegrationTokenDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotEmpty List<String> scopes,
            LocalDateTime expiresAt
    ) {
    }

    public record Item(
            UUID id,
            String name,
            String tokenPrefix,
            List<String> scopes,
            LocalDateTime expiresAt,
            LocalDateTime lastUsedAt,
            LocalDateTime revokedAt,
            LocalDateTime createdAt
    ) {
    }

    public record Created(Item token, String secret) {
    }
}
