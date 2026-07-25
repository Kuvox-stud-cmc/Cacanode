package com.cacanode.api.billing.model;

public record EntitlementSnapshot(
        Integer maxMessages,
        Integer maxDocuments,
        Integer maxTeamMembers,
        Integer maxStorageMb,
        Long maxActiveJobs,
        Long maxVerifiedApplications,
        Long maxInterviewSeconds,
        Long maxCvAnalyses,
        Long maxRecruitmentStorageBytes,
        boolean apiAccess,
        boolean webhooks,
        boolean advancedAnalytics,
        boolean customBranding
) {
}
