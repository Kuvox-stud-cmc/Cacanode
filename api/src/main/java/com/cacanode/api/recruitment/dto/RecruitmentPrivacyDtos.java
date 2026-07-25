package com.cacanode.api.recruitment.dto;

import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public final class RecruitmentPrivacyDtos {
    private RecruitmentPrivacyDtos() {}
    public record AdminRequest(@NotBlank @Size(max=255) String verificationReference) {}
    public record Confirmation(@NotBlank String token) {}
    public record Status(UUID id,UUID applicationId,UUID candidateId,
            PrivacyDeletionRequesterKind requesterKind,PrivacyDeletionStatus status,int attempts,
            String lastErrorCode,Instant confirmedAt,Instant completedAt,Instant exhaustedAt,LocalDateTime createdAt) {}
}
