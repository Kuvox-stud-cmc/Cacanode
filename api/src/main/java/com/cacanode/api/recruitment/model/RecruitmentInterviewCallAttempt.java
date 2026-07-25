package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name="recruitment_interview_call_attempts")
public class RecruitmentInterviewCallAttempt extends BaseEntity {
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="interview_id",nullable=false,updatable=false) private UUID interviewId;
    @Column(name="application_id",nullable=false,updatable=false) private UUID applicationId;
    @Column(name="job_id",nullable=false,updatable=false) private UUID jobId;
    @Column(name="session_id",nullable=false,updatable=false) private UUID sessionId;
    @Column(name="schedule_version",nullable=false,updatable=false) private long scheduleVersion;
    @Column(name="attempt_number",nullable=false,updatable=false) private int attemptNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private RecruitmentEnums.CallAttemptStatus status;
    @Column(name="template_revision_id",nullable=false,updatable=false) private UUID templateRevisionId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="prepared_session",columnDefinition="jsonb") private String preparedSession;
    @Column(name="prepared_session_sha256",length=64) private String preparedSessionSha256;
    @Column(name="prepared_snapshot_version",length=40) private String preparedSnapshotVersion;
    @Column(name="cv_analysis_id",updatable=false) private UUID cvAnalysisId;
    @Column(name="cv_analysis_sha256",length=64,updatable=false) private String cvAnalysisSha256;
    @Column(name="destination_e164",updatable=false,length=20) private String destinationE164;
    @Column(name="runtime_token_sha256",length=64) private String runtimeTokenSha256;
    @Column(name="runtime_token_expires_at") private Instant runtimeTokenExpiresAt;
    @Column(name="twilio_call_sid",length=40) private String twilioCallSid;
    @Column(name="callback_sequence",nullable=false) private long callbackSequence=-1;
    @Column(name="preparation_attempts",nullable=false) private int preparationAttempts;
    @Column(name="cancellation_attempts",nullable=false) private int cancellationAttempts;
    @Column(name="termination_attempts",nullable=false) private int terminationAttempts;
    @Column(name="create_outcome_uncertain",nullable=false) private boolean createOutcomeUncertain;
    @Column(name="create_uncertain_until") private Instant createUncertainUntil;
    @Column(name="next_retry_at") private Instant nextRetryAt;
    @Column(name="failure_code",length=100) private String failureCode;
    @Column(name="call_duration_seconds") private Integer callDurationSeconds;
    @Column(name="answered_at") private Instant answeredAt;
    @Column(name="consented_at") private Instant consentedAt;
    @Column(name="terminal_at") private Instant terminalAt;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Column(name="expires_at") private Instant expiresAt;
    @Version @Column(name="version",nullable=false) private long version;
}
