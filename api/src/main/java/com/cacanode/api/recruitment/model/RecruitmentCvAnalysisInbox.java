package com.cacanode.api.recruitment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name="recruitment_cv_analysis_inbox")
public class RecruitmentCvAnalysisInbox {
    @Id @Column(name="event_id") private UUID eventId;
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="analysis_id",nullable=false) private UUID analysisId;
    @Column(name="application_id",nullable=false) private UUID applicationId;
    @Column(name="payload_sha256",nullable=false,length=64) private String payloadSha256;
    @Column(name="processing_result",nullable=false) private String processingResult;
    @CreationTimestamp @Column(name="processed_at",nullable=false,updatable=false) private LocalDateTime processedAt;
}
