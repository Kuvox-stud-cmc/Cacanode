package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_candidate_sessions")
public class RecruitmentCandidateSession extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "application_id", nullable = false) private UUID applicationId;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Column(name = "access_token_hash", nullable = false, length = 64) private String accessTokenHash;
    @Column(name = "refresh_token_hash", nullable = false, length = 64) private String refreshTokenHash;
    @Column(name = "csrf_token_hash", nullable = false, length = 64) private String csrfTokenHash;
    @Column(name = "access_expires_at", nullable = false) private LocalDateTime accessExpiresAt;
    @Column(name = "refresh_expires_at", nullable = false) private LocalDateTime refreshExpiresAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
    @Version @Column(name = "version", nullable = false) private long version;
}
