package com.cacanode.api.tenant.api;

import com.cacanode.api.tenant.enums.TenantPlan;
import com.cacanode.api.tenant.enums.TenantStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApplyTenantEntitlementsCommand(
        UUID tenantId,
        TenantPlan plan,
        TenantStatus status,
        Integer maxMessages,
        Integer maxDocuments,
        Integer maxTeamMembers,
        Integer maxStorageMb,
        LocalDateTime trialEndsAt,
        LocalDateTime quotaAnchorAt,
        LocalDateTime paidThroughAt,
        LocalDateTime graceEndsAt,
        boolean apiAccess,
        boolean webhooks,
        boolean advancedAnalytics,
        boolean customBranding
) {
}
