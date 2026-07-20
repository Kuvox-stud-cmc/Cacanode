package com.cacanode.api.document.dto;

import com.cacanode.api.chat.dto.ChatDtos;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PublicEvidenceDtos {
    private PublicEvidenceDtos() {
    }

    public record Response(
            @JsonProperty("document_id") UUID documentId,
            @JsonProperty("source_name") String sourceName,
            String focus,
            @JsonProperty("expires_at") Instant expiresAt,
            List<ChatDtos.DocumentUnitResponse> units
    ) {
    }
}
