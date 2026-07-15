package com.cacanode.api.billing.model;

public record EntitlementSnapshot(
        Integer maxMessages,
        Integer maxDocuments,
        Integer maxTeamMembers,
        Integer maxStorageMb,
        boolean apiAccess,
        boolean webhooks,
        boolean advancedAnalytics,
        boolean customBranding
) {
}
