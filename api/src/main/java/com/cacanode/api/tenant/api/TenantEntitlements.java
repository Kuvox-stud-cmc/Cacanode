package com.cacanode.api.tenant.api;

import com.cacanode.api.tenant.api.TenantPlan;
import com.cacanode.api.tenant.api.TenantStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantEntitlements(
        UUID tenantId,
        TenantPlan plan,
        TenantStatus status,
        Integer maxMessages,
        Integer maxDocuments,
        Integer maxTeamMembers,
        Integer maxStorageMb,
        LocalDateTime quotaAnchorAt,
        LocalDateTime paidThroughAt,
        LocalDateTime graceEndsAt,
        boolean apiAccess,
        boolean webhooks,
        boolean advancedAnalytics,
        boolean customBranding
) {
    public boolean unlimitedMessages() { return maxMessages == null; }
    public boolean unlimitedDocuments() { return maxDocuments == null; }
    public boolean unlimitedMembers() { return maxTeamMembers == null; }
    public boolean unlimitedStorage() { return maxStorageMb == null; }
}
