package com.cacanode.api.recruitment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter @Setter @Entity
@Table(name = "recruitment_tenant_settings")
public class RecruitmentTenantSettings {
    @Id @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Enumerated(EnumType.STRING) @Column(name = "default_automation_mode", nullable = false)
    private RecruitmentEnums.AutomationMode defaultAutomationMode = RecruitmentEnums.AutomationMode.MANUAL;
    @Enumerated(EnumType.STRING) @Column(name = "cv_ai_mode", nullable = false)
    private RecruitmentEnums.CvAiMode cvAiMode = RecruitmentEnums.CvAiMode.OFF;
    @Column(name = "default_template_revision_id") private UUID defaultTemplateRevisionId;
    @Column(name = "recording_enabled", nullable = false) private boolean recordingEnabled;
    @Column(name = "recording_retention_days", nullable = false) private int recordingRetentionDays;
    @Column(name = "scheduling_timezone", nullable = false) private String schedulingTimezone = "Asia/Ho_Chi_Minh";
    @Column(name = "slot_grid_minutes", nullable = false) private int slotGridMinutes = 15;
    @Column(name = "minimum_notice_minutes", nullable = false) private int minimumNoticeMinutes = 120;
    @Column(name = "booking_horizon_days", nullable = false) private int bookingHorizonDays = 30;
    @Column(name = "invitation_lifetime_days", nullable = false) private int invitationLifetimeDays = 7;
    @Column(name = "reschedule_cutoff_minutes", nullable = false) private int rescheduleCutoffMinutes = 120;
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "reminder_offsets_minutes", nullable = false)
    private List<Integer> reminderOffsetsMinutes = List.of(1440, 60);
    @Version @Column(name = "version", nullable = false) private long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
