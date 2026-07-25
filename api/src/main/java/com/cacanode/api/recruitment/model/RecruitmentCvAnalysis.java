package com.cacanode.api.recruitment.model;

import com.cacanode.api.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name="recruitment_cv_analyses")
public class RecruitmentCvAnalysis extends BaseEntity {
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="application_id",nullable=false,updatable=false) private UUID applicationId;
    @Column(name="cv_id",nullable=false,updatable=false) private UUID cvId;
    @Column(name="cv_sha256",nullable=false,updatable=false,length=64) private String cvSha256;
    @Enumerated(EnumType.STRING) @Column(name="analysis_mode",nullable=false,updatable=false)
    private RecruitmentEnums.CvAiMode analysisMode;
    @Column(name="policy_version",nullable=false,updatable=false) private String policyVersion;
    @Column(name="model_version",nullable=false,updatable=false) private String modelVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private RecruitmentEnums.CvAnalysisRecordStatus status;
    @Column(name="request_event_id",updatable=false) private UUID requestEventId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="request_payload",updatable=false,columnDefinition="jsonb") private String requestPayload;
    @Column(name="request_payload_sha256",updatable=false,length=64) private String requestPayloadSha256;
    @Column(name="publish_attempts",nullable=false) private int publishAttempts;
    @Column(name="next_publish_at") private LocalDateTime nextPublishAt;
    @Column(name="published_at") private LocalDateTime publishedAt;
    @Column(name="completed_at") private LocalDateTime completedAt;
    @Column(name="failure_code") private String failureCode;
    @Column(columnDefinition="text") private String summary;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private String evidence="[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private String skills="[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="personalized_questions",nullable=false,columnDefinition="jsonb") private String personalizedQuestions="[]";
    @Column(name="outcome_event_id") private UUID outcomeEventId;
    @Column(name="outcome_payload_sha256",length=64) private String outcomePayloadSha256;
    @Version @Column(name="version",nullable=false) private long version;
}
