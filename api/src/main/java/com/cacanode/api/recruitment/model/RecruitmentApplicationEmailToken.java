package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_application_email_tokens")
public class RecruitmentApplicationEmailToken extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "application_id", nullable = false) private UUID applicationId;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecruitmentEnums.EmailTokenPurpose purpose;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "consumed_at") private LocalDateTime consumedAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
}
