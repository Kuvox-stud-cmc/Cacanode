package com.cacanode.api.tenant.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public final class CustomerAnswerPromptDtos {
    private CustomerAnswerPromptDtos() {
    }

    public record UpdateRequest(
            @NotNull(message = "Prompt is required")
            String prompt
    ) {
    }

    public record Response(
            String prompt,
            boolean usingDefault,
            LocalDateTime updatedAt
    ) {
    }
}
