package com.cacanode.api.recruitment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name="recruitment_privacy_deletion_requests")
public class RecruitmentPrivacyDeletionRequest {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="application_id",nullable=false,updatable=false) private UUID applicationId;
    @Column(name="candidate_id",nullable=false,updatable=false) private UUID candidateId;
    @Enumerated(EnumType.STRING) @Column(name="requester_kind",nullable=false,updatable=false)
    private RecruitmentEnums.PrivacyDeletionRequesterKind requesterKind;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private RecruitmentEnums.PrivacyDeletionStatus status;
    @Column(name="verification_reference") private String verificationReference;
    @Column(nullable=false) private int attempts;
    @Column(name="next_attempt_at") private Instant nextAttemptAt;
    @Column(name="last_error_code") private String lastErrorCode;
    @Column(name="confirmed_at") private Instant confirmedAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="exhausted_at") private Instant exhaustedAt;
    @Version @Column(nullable=false) private long version;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
