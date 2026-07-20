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
    private String catalogVersion = "2026-07-15";
    private int trialDays = 14;
    private int graceDays = 3;
    private int checkoutMinutes = 30;
    private long proMonthlyPriceVnd = 1_199_000L;
    private long proAnnualPriceVnd = 11_990_000L;
    private String frontendReturnUrl = "http://localhost:3000/settings?tab=quota&payment=return";
    private String frontendCancelUrl = "http://localhost:3000/settings?tab=quota&payment=cancel";
    private String salesUrl = "mailto:sales@cacanode.com";
    private final PayOs payos = new PayOs();
    private final Entitlements starter = new Entitlements(500, 3, 1, 512, false, false, false, false);
    private final Entitlements pro = new Entitlements(10_000, 50, 5, 10_240, true, true, true, true);
    private final Entitlements enterprise = new Entitlements(null, null, null, null, true, true, true, true);

    public EntitlementSnapshot starterEntitlements() {
        return starter.snapshot();
    }

    public EntitlementSnapshot proEntitlements() {
        return pro.snapshot();
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
        private boolean apiAccess;
        private boolean webhooks;
        private boolean advancedAnalytics;
        private boolean customBranding;

        public Entitlements() {
        }

        public Entitlements(Integer maxMessages, Integer maxDocuments, Integer maxTeamMembers,
                            Integer maxStorageMb, boolean apiAccess, boolean webhooks,
                            boolean advancedAnalytics, boolean customBranding) {
            this.maxMessages = maxMessages;
            this.maxDocuments = maxDocuments;
            this.maxTeamMembers = maxTeamMembers;
            this.maxStorageMb = maxStorageMb;
            this.apiAccess = apiAccess;
            this.webhooks = webhooks;
            this.advancedAnalytics = advancedAnalytics;
            this.customBranding = customBranding;
        }

        EntitlementSnapshot snapshot() {
            return new EntitlementSnapshot(maxMessages, maxDocuments, maxTeamMembers, maxStorageMb,
                    apiAccess, webhooks, advancedAnalytics, customBranding);
        }
    }
}
