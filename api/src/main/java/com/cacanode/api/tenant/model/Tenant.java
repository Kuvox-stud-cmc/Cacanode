package com.cacanode.api.tenant.model;

import com.cacanode.api.common.model.BaseEntity;
import com.cacanode.api.tenant.CustomerAnswerPromptDefaults;
import com.cacanode.api.tenant.enums.TenantPlan;
import com.cacanode.api.tenant.enums.TenantStatus;
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
    @Column(name = "max_documents", nullable = false)
    private int maxDocuments = 150;

    @Column(name = "max_messages", nullable = false)
    private int maxMessages = 5000;

    @Column(name = "max_storage_mb", nullable = false)
    private int maxStorageMb = 5120;

    @Column(name = "customer_answer_prompt", nullable = false, columnDefinition = "TEXT")
    private String customerAnswerPrompt = CustomerAnswerPromptDefaults.PLATFORM_DEFAULT;

}
