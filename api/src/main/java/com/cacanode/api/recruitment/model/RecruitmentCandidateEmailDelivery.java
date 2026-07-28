package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_candidate_email_deliveries")
public class RecruitmentCandidateEmailDelivery extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "interview_id", nullable = false) private UUID interviewId;
    @Column(name = "application_id", nullable = false) private UUID applicationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecruitmentEnums.CandidateEmailKind kind;
    @Column(name = "dedupe_key", nullable = false) private String dedupeKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecruitmentEnums.CandidateEmailState state = RecruitmentEnums.CandidateEmailState.PENDING;
    @Column(name = "due_at", nullable = false) private LocalDateTime dueAt;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private LocalDateTime nextAttemptAt;
    @Column(name = "sent_at") private LocalDateTime sentAt;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    @Column(name = "last_error") private String lastError;
    @Version @Column(name = "version", nullable = false) private long version;
}
