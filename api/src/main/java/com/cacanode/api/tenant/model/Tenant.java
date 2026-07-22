package com.cacanode.api.tenant.model;

import com.cacanode.api.common.model.BaseEntity;
import com.cacanode.api.tenant.CustomerAnswerPromptDefaults;
import com.cacanode.api.tenant.api.TenantPlan;
import com.cacanode.api.tenant.api.TenantStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name="tenants")
public class Tenant extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", unique = true, nullable = false, length = 100)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 50)
    private TenantPlan plan = TenantPlan.TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TenantStatus status = TenantStatus.PENDING;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspended_reason")
    private String suspendedReason;

    // Usage quota
    @Column(name = "max_documents")
    private Integer maxDocuments = 50;

    @Column(name = "max_messages")
    private Integer maxMessages = 10_000;

    @Column(name = "max_storage_mb")
    private Integer maxStorageMb = 10_240;

    @Column(name = "max_team_members")
    private Integer maxTeamMembers = 5;

    @Column(name = "quota_anchor_at")
    private LocalDateTime quotaAnchorAt;

    @Column(name = "paid_through_at")
    private LocalDateTime paidThroughAt;

    @Column(name = "grace_ends_at")
    private LocalDateTime graceEndsAt;

    @Column(name = "api_access_enabled", nullable = false)
    private boolean apiAccessEnabled = true;

    @Column(name = "webhooks_enabled", nullable = false)
    private boolean webhooksEnabled = true;

    @Column(name = "advanced_analytics_enabled", nullable = false)
    private boolean advancedAnalyticsEnabled = true;

    @Column(name = "custom_branding_enabled", nullable = false)
    private boolean customBrandingEnabled = true;

    @Column(name = "customer_answer_prompt", nullable = false, columnDefinition = "TEXT")
    private String customerAnswerPrompt = CustomerAnswerPromptDefaults.PLATFORM_DEFAULT;

    @PrePersist
    void applyTenantAnswerPromptDefault() {
        if (CustomerAnswerPromptDefaults.shouldUseTenantDefault(customerAnswerPrompt, name)) {
            customerAnswerPrompt = CustomerAnswerPromptDefaults.forTenant(name);
        }
    }

}
