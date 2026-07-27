package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter @Setter @Entity
@Table(name = "recruitment_jobs")
public class RecruitmentJob extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "public_id", nullable = false, updatable = false) private UUID publicId;
    @Column(name = "title", nullable = false) private String title;
    @Column(name = "description", nullable = false, columnDefinition = "text") private String description;
    @Column(name = "description_html", columnDefinition = "text") private String descriptionHtml;
    private String department;
    private String location;
    @Enumerated(EnumType.STRING) @Column(name = "employment_type") private RecruitmentEnums.EmploymentType employmentType;
    @Enumerated(EnumType.STRING) @Column(name = "work_mode") private RecruitmentEnums.WorkMode workMode;
    @Enumerated(EnumType.STRING) @Column(name = "experience_level") private RecruitmentEnums.ExperienceLevel experienceLevel;
    @Column(nullable = false) private String language;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecruitmentEnums.JobStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "cv_policy", nullable = false) private RecruitmentEnums.CvPolicy cvPolicy;
    @Enumerated(EnumType.STRING) @Column(name = "automation_mode_override") private RecruitmentEnums.AutomationMode automationModeOverride;
    @Enumerated(EnumType.STRING) @Column(name = "cv_ai_mode_override") private RecruitmentEnums.CvAiMode cvAiModeOverride;
    @Enumerated(EnumType.STRING) @Column(name = "effective_automation_mode") private RecruitmentEnums.AutomationMode effectiveAutomationMode;
    @Enumerated(EnumType.STRING) @Column(name = "effective_cv_ai_mode") private RecruitmentEnums.CvAiMode effectiveCvAiMode;
    @Column(name = "recording_enabled", nullable = false) private boolean recordingEnabled;
    @Column(name = "recording_retention_days", nullable = false) private int recordingRetentionDays;
    @Column(name = "template_revision_id") private UUID templateRevisionId;
    @Column(name = "closing_at") private LocalDateTime closingAt;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "paused_at") private LocalDateTime pausedAt;
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(name = "archived_at") private LocalDateTime archivedAt;
    @Column(name = "active_job_reservation_id") private UUID activeJobReservationId;
    @Column(name = "frozen_company_name") private String frozenCompanyName;
    @Column(name = "frozen_company_slug") private String frozenCompanySlug;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "screening_config", nullable = false, columnDefinition = "jsonb")
    private String screeningConfig = "[]";
    @Version @Column(name = "version", nullable = false) private long version;
}
