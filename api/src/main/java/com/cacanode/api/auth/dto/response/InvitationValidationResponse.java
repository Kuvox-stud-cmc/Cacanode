package com.cacanode.api.auth.dto.response;

import com.cacanode.api.tenant.enums.UserRole;
import java.time.LocalDateTime;

public record InvitationValidationResponse(
        String email,
        String tenantName,
        UserRole role,
        LocalDateTime expiresAt) {}
