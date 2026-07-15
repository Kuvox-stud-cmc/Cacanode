package com.cacanode.api.tenant.dto;

import com.cacanode.api.tenant.enums.WidgetPosition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class WidgetConfigDtos {
    private WidgetConfigDtos() {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 255) String displayName,
            @NotBlank @Size(max = 2000) String welcomeMessage,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String primaryColor,
            WidgetPosition position,
            boolean active,
            List<String> allowedOrigins,
            Boolean hideCacanodeBranding
    ) {
    }

    public record Response(
            UUID chatbotId,
            String displayName,
            String welcomeMessage,
            String primaryColor,
            WidgetPosition position,
            boolean active,
            List<String> allowedOrigins,
            boolean hideCacanodeBranding,
            boolean showCacanodeBranding
    ) {
    }
}
