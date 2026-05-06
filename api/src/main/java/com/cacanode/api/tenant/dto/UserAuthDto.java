package com.cacanode.api.tenant.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserAuthDto {
    private UUID userId;
    private UUID tenantId;
    private String email;
    private String fullName;
    private String plan;
    private String passwordHash;
    private String role;
    private String status;
    private String tenantStatus;
}
