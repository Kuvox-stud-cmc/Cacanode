package com.cacanode.api.common.cache;

import com.cacanode.api.common.config.CacheProperties;

import java.time.Duration;

public enum BusinessCache {
    WIDGET_CONFIG("widget-config") {
        public boolean domainEnabled(CacheProperties p) { return p.isWidgetConfigEnabled(); }
        public int ttlSeconds(CacheProperties p) { return p.getWidgetConfigTtlSeconds(); }
    },
    CUSTOMER_ANSWER_PROMPT("customer-answer-prompt") {
        public boolean domainEnabled(CacheProperties p) { return p.isCustomerAnswerPromptEnabled(); }
        public int ttlSeconds(CacheProperties p) { return p.getCustomerAnswerPromptTtlSeconds(); }
    },
    BILLING_ACCOUNT("billing-account") {
        public boolean domainEnabled(CacheProperties p) { return p.isBillingAccountEnabled(); }
        public int ttlSeconds(CacheProperties p) { return p.getBillingAccountTtlSeconds(); }
    },
    WORKSPACE("workspace") {
        public boolean domainEnabled(CacheProperties p) { return p.isWorkspaceEnabled(); }
        public int ttlSeconds(CacheProperties p) { return p.getWorkspaceTtlSeconds(); }
    },
    DASHBOARD("dashboard") {
        public boolean domainEnabled(CacheProperties p) { return p.isDashboardEnabled(); }
        public int ttlSeconds(CacheProperties p) { return p.getDashboardTtlSeconds(); }
    },
    ANALYTICS("analytics") {
        public boolean domainEnabled(CacheProperties p) { return p.isAnalyticsEnabled(); }
        public int ttlSeconds(CacheProperties p) { return p.getAnalyticsTtlSeconds(); }
    },
    USER_DIRECTORY("user-directory") {
        public boolean domainEnabled(CacheProperties p) { return p.isUserDirectoryEnabled(); }
        public int ttlSeconds(CacheProperties p) { return p.getUserDirectoryTtlSeconds(); }
    },
    DOCUMENT_LIST("document-list") {
        public boolean domainEnabled(CacheProperties p) { return p.isDocumentListEnabled(); }
        public int ttlSeconds(CacheProperties p) { return p.getDocumentListTtlSeconds(); }
    };

    private final String label;

    BusinessCache(String label) {
        this.label = label;
    }

    public String label() { return label; }
    public abstract boolean domainEnabled(CacheProperties properties);
    public abstract int ttlSeconds(CacheProperties properties);

    public boolean enabled(CacheProperties properties) {
        return properties.isEnabled() && properties.isBusinessReadEnabled() && domainEnabled(properties);
    }

    public Duration ttl(CacheProperties properties) {
        return Duration.ofSeconds(Math.max(1, ttlSeconds(properties)));
    }
}
