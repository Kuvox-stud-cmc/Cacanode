package com.cacanode.api.billing.config;

import com.cacanode.api.billing.model.EntitlementSnapshot;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.billing")
public class BillingProperties {
    private boolean payosEnabled = false;
    private String catalogVersion = "2026-07-23";
    private int trialDays = 14;
    private int graceDays = 3;
    private int checkoutMinutes = 30;
    private long proMonthlyPriceVnd = 1_199_000L;
    private long proAnnualPriceVnd = 11_990_000L;
    private long businessMonthlyPriceVnd = 3_499_000L;
    private long businessAnnualPriceVnd = 34_990_000L;
    private long hiringReservationTtlHours = 24L;
    private long hiringReservationReaperMs = 300_000L;
    private String frontendReturnUrl = "http://localhost:3000/settings?tab=quota&payment=return";
    private String frontendCancelUrl = "http://localhost:3000/settings?tab=quota&payment=cancel";
    private String salesUrl = "mailto:sales@cacanode.com";
    private final PayOs payos = new PayOs();
    private final Entitlements starter = new Entitlements(500, 3, 1, 512,
            1L, 25L, 0L, 0L, 52_428_800L, false, false, false, false);
    private final Entitlements trial = new Entitlements(10_000, 50, 5, 10_240,
            1L, 25L, 1_200L, 5L, 104_857_600L, true, true, true, true);
    private final Entitlements pro = new Entitlements(10_000, 50, 5, 10_240,
            3L, 150L, 3_600L, 100L, 1_073_741_824L, true, true, true, true);
    private final Entitlements business = new Entitlements(50_000, 250, 15, 51_200,
            10L, 1_000L, 18_000L, 500L, 10_737_418_240L, true, true, true, true);
    private final Entitlements enterprise = new Entitlements(null, null, null, null,
            0L, 0L, 0L, 0L, 0L, true, true, true, true);

    public EntitlementSnapshot starterEntitlements() {
        return starter.snapshot();
    }

    public EntitlementSnapshot proEntitlements() {
        return pro.snapshot();
    }

    public EntitlementSnapshot trialEntitlements() {
        return trial.snapshot();
    }

    public EntitlementSnapshot businessEntitlements() {
        return business.snapshot();
    }

    public EntitlementSnapshot enterpriseEntitlements() {
        return enterprise.snapshot();
    }

    @Getter
    @Setter
    public static class PayOs {
        private String clientId = "";
        private String apiKey = "";
        private String checksumKey = "";
        private String webhookUrl = "";
        private int maxRetries = 3;
    }

    @Getter
    @Setter
    public static class Entitlements {
        private Integer maxMessages;
        private Integer maxDocuments;
        private Integer maxTeamMembers;
        private Integer maxStorageMb;
        private Long maxActiveJobs;
        private Long maxVerifiedApplications;
        private Long maxInterviewSeconds;
        private Long maxCvAnalyses;
        private Long maxRecruitmentStorageBytes;
        private boolean apiAccess;
        private boolean webhooks;
        private boolean advancedAnalytics;
        private boolean customBranding;

        public Entitlements() {
        }

        public Entitlements(Integer maxMessages, Integer maxDocuments, Integer maxTeamMembers,
                            Integer maxStorageMb, Long maxActiveJobs, Long maxVerifiedApplications,
                            Long maxInterviewSeconds, Long maxCvAnalyses, Long maxRecruitmentStorageBytes,
                            boolean apiAccess, boolean webhooks,
                            boolean advancedAnalytics, boolean customBranding) {
            this.maxMessages = maxMessages;
            this.maxDocuments = maxDocuments;
            this.maxTeamMembers = maxTeamMembers;
            this.maxStorageMb = maxStorageMb;
            this.maxActiveJobs = maxActiveJobs;
            this.maxVerifiedApplications = maxVerifiedApplications;
            this.maxInterviewSeconds = maxInterviewSeconds;
            this.maxCvAnalyses = maxCvAnalyses;
            this.maxRecruitmentStorageBytes = maxRecruitmentStorageBytes;
            this.apiAccess = apiAccess;
            this.webhooks = webhooks;
            this.advancedAnalytics = advancedAnalytics;
            this.customBranding = customBranding;
        }

        EntitlementSnapshot snapshot() {
            return new EntitlementSnapshot(maxMessages, maxDocuments, maxTeamMembers, maxStorageMb,
                    maxActiveJobs, maxVerifiedApplications, maxInterviewSeconds, maxCvAnalyses,
                    maxRecruitmentStorageBytes,
                    apiAccess, webhooks, advancedAnalytics, customBranding);
        }
    }
}
