package com.cacanode.api.document.dto;

import com.cacanode.api.document.enums.DocumentVisibility;
import jakarta.validation.constraints.NotNull;

public record DocumentVisibilityUpdateRequest(@NotNull DocumentVisibility visibility) {
}
