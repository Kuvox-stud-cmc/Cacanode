package com.cacanode.api.recruitment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Entity
@Table(name="recruitment_tenant_activation")
public class RecruitmentTenantActivation {
    @Id @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Enumerated(EnumType.STRING) @Column(name="rollout_stage",nullable=false)
    private RecruitmentEnums.RolloutStage rolloutStage=RecruitmentEnums.RolloutStage.OFF;
    @Column(name="master_enabled",nullable=false) private boolean masterEnabled;
    @Column(name="automation_enabled",nullable=false) private boolean automationEnabled;
    @Column(name="cv_ai_enabled",nullable=false) private boolean cvAiEnabled;
    @Column(name="calling_enabled",nullable=false) private boolean callingEnabled;
    @Column(name="recording_enabled",nullable=false) private boolean recordingEnabled;
    @Column(name="public_discovery_enabled",nullable=false) private boolean publicDiscoveryEnabled;
    @Version @Column(name="version",nullable=false) private long version;
    @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
