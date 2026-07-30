package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name = "recruitment_applications")
public class RecruitmentApplication extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Column(name = "candidate_id", nullable = false) private UUID candidateId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecruitmentEnums.ApplicationStatus status;
    @Column(name = "submitted_at") private LocalDateTime submittedAt;
    @Column(name = "verified_at") private LocalDateTime verifiedAt;
    @Column(name = "withdrawn_at") private LocalDateTime withdrawnAt;
    @Column(nullable = false) private String locale;
    @Column(name = "privacy_consent_at") private LocalDateTime privacyConsentAt;
    @Column(name = "cv_use_disclosed_at") private LocalDateTime cvUseDisclosedAt;
    @Column(name = "cv_ai_consent_at") private LocalDateTime cvAiConsentAt;
    @Column(name = "cv_present", nullable = false) private boolean cvPresent;
    @Enumerated(EnumType.STRING) @Column(name = "cv_analysis_status", nullable = false) private RecruitmentEnums.CvAnalysisStatus cvAnalysisStatus;
    @Enumerated(EnumType.STRING) @Column(name = "cv_ai_mode_snapshot", nullable = false, updatable = false)
    private RecruitmentEnums.CvAiMode cvAiModeSnapshot = RecruitmentEnums.CvAiMode.OFF;
    @Column(name = "cv_ai_policy_version", nullable = false, updatable = false) private String cvAiPolicyVersion="cv-redaction-fit-v2";
    @Column(name = "cv_ai_model_version", nullable = false, updatable = false) private String cvAiModelVersion="resume-analysis-v2";
    @Column(name = "active_cv_analysis_id") private UUID activeCvAnalysisId;
    @Column(name = "pending_cv_analysis_id") private UUID pendingCvAnalysisId;
    @Column(name = "template_revision_id", nullable = false) private UUID templateRevisionId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "template_snapshot", nullable = false, columnDefinition = "jsonb") private String templateSnapshot;
    @Column(name = "template_snapshot_sha256", nullable = false, length = 64) private String templateSnapshotSha256;
    @Column(name = "template_snapshot_version", nullable = false) private String templateSnapshotVersion;
    @Column(name = "overall_score") private BigDecimal overallScore;
    @Column(name = "english_band", length = 30) private String englishBand;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "screening_config_snapshot", nullable = false, updatable=false, columnDefinition = "jsonb")
    private String screeningConfigSnapshot = "[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "screening_answers", nullable = false, columnDefinition = "jsonb")
    private String screeningAnswers = "[]";
    @Enumerated(EnumType.STRING) @Column(name = "automation_mode_snapshot", nullable = false, updatable=false)
    private RecruitmentEnums.AutomationMode automationModeSnapshot = RecruitmentEnums.AutomationMode.MANUAL;
    @Enumerated(EnumType.STRING) @Column(name = "automation_outcome", nullable = false)
    private RecruitmentEnums.AutomationOutcome automationOutcome = RecruitmentEnums.AutomationOutcome.PENDING;
    @Column(name = "automation_evaluated_at") private LocalDateTime automationEvaluatedAt;
    @Version @Column(name = "version", nullable = false) private long version;
}
