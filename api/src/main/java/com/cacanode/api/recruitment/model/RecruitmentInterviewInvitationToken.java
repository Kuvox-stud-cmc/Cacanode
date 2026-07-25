package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_interview_invitation_tokens")
public class RecruitmentInterviewInvitationToken extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "interview_id", nullable = false) private UUID interviewId;
    @Column(name = "application_id", nullable = false) private UUID applicationId;
    @Column(name = "delivery_id", nullable = false) private UUID deliveryId;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
    @Column(name = "last_used_at") private LocalDateTime lastUsedAt;
    @Version @Column(name = "version", nullable = false) private long version;
}
