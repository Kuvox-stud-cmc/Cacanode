package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_interviews")
public class RecruitmentInterview extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "application_id", nullable = false) private UUID applicationId;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecruitmentEnums.InterviewStatus status;
    @Column(name = "template_revision_id", nullable = false) private UUID templateRevisionId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "template_snapshot", nullable = false, columnDefinition = "jsonb") private String templateSnapshot;
    @Column(name = "template_snapshot_sha256", nullable = false, length = 64) private String templateSnapshotSha256;
    @Column(name = "template_snapshot_version", nullable = false) private String templateSnapshotVersion;
    @Column(name = "scheduled_at") private LocalDateTime scheduledAt;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "overall_score") private BigDecimal overallScore;
    @Column(name = "english_band", length = 30) private String englishBand;
    @Column(name = "recording_enabled", nullable = false) private boolean recordingEnabled;
    @Column(name = "recording_retention_days", nullable = false) private int recordingRetentionDays;
    @Column(name = "recording_expires_at") private LocalDateTime recordingExpiresAt;
    @Column(name = "invited_at") private LocalDateTime invitedAt;
    @Column(name = "invitation_expires_at") private LocalDateTime invitationExpiresAt;
    @Column(name = "scheduled_start_at") private Instant scheduledStartAt;
    @Column(name = "scheduled_end_at") private Instant scheduledEndAt;
    @Column(name = "scheduling_timezone") private String schedulingTimezone;
    @Column(name = "schedule_version", nullable = false) private long scheduleVersion;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    @Column(name = "expired_at") private LocalDateTime expiredAt;
    @Column(name = "quota_reservation_id") private UUID quotaReservationId;
    @Column(name = "quota_reserved_seconds") private Long quotaReservedSeconds;
    @Column(name = "quota_reservation_expires_at") private LocalDateTime quotaReservationExpiresAt;
    @Column(name = "reschedule_count", nullable = false) private int rescheduleCount;
    @Column(name = "active_call_attempt_id") private UUID activeCallAttemptId;
    @Version @Column(name = "version", nullable = false) private long version;
}
