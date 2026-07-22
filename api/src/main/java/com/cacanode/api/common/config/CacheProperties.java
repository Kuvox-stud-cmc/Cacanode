package com.cacanode.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    private boolean enabled = false;
    private String keyPrefix = "ccn:v2";
    private int ttlJitterPercent = 10;
    private boolean integrationTokenEnabled = false;
    private int integrationTokenTtlSeconds = 60;
    private boolean businessReadEnabled = false;
    private boolean widgetConfigEnabled = false;
    private int widgetConfigTtlSeconds = 120;
    private boolean customerAnswerPromptEnabled = false;
    private int customerAnswerPromptTtlSeconds = 120;
    private boolean billingAccountEnabled = false;
    private int billingAccountTtlSeconds = 30;
    private boolean workspaceEnabled = false;
    private int workspaceTtlSeconds = 300;
    private boolean dashboardEnabled = false;
    private int dashboardTtlSeconds = 20;
    private boolean analyticsEnabled = false;
    private int analyticsTtlSeconds = 60;
    private boolean userDirectoryEnabled = false;
    private int userDirectoryTtlSeconds = 30;
    private boolean documentListEnabled = false;
    private int documentListTtlSeconds = 15;
    private boolean embeddingEnabled = false;
    private boolean retrievalEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getTtlJitterPercent() {
        return ttlJitterPercent;
    }

    public void setTtlJitterPercent(int ttlJitterPercent) {
        this.ttlJitterPercent = ttlJitterPercent;
    }

    public boolean isIntegrationTokenEnabled() {
        return integrationTokenEnabled;
    }

    public void setIntegrationTokenEnabled(boolean integrationTokenEnabled) {
        this.integrationTokenEnabled = integrationTokenEnabled;
    }

    public int getIntegrationTokenTtlSeconds() {
        return integrationTokenTtlSeconds;
    }

    public void setIntegrationTokenTtlSeconds(int integrationTokenTtlSeconds) {
        this.integrationTokenTtlSeconds = integrationTokenTtlSeconds;
    }

    public boolean isBusinessReadEnabled() {
        return businessReadEnabled;
    }

    public void setBusinessReadEnabled(boolean businessReadEnabled) {
        this.businessReadEnabled = businessReadEnabled;
    }

    public boolean isWidgetConfigEnabled() { return widgetConfigEnabled; }
    public void setWidgetConfigEnabled(boolean value) { this.widgetConfigEnabled = value; }
    public int getWidgetConfigTtlSeconds() { return widgetConfigTtlSeconds; }
    public void setWidgetConfigTtlSeconds(int value) { this.widgetConfigTtlSeconds = value; }
    public boolean isCustomerAnswerPromptEnabled() { return customerAnswerPromptEnabled; }
    public void setCustomerAnswerPromptEnabled(boolean value) { this.customerAnswerPromptEnabled = value; }
    public int getCustomerAnswerPromptTtlSeconds() { return customerAnswerPromptTtlSeconds; }
    public void setCustomerAnswerPromptTtlSeconds(int value) { this.customerAnswerPromptTtlSeconds = value; }
    public boolean isBillingAccountEnabled() { return billingAccountEnabled; }
    public void setBillingAccountEnabled(boolean value) { this.billingAccountEnabled = value; }
    public int getBillingAccountTtlSeconds() { return billingAccountTtlSeconds; }
    public void setBillingAccountTtlSeconds(int value) { this.billingAccountTtlSeconds = value; }
    public boolean isWorkspaceEnabled() { return workspaceEnabled; }
    public void setWorkspaceEnabled(boolean value) { this.workspaceEnabled = value; }
    public int getWorkspaceTtlSeconds() { return workspaceTtlSeconds; }
    public void setWorkspaceTtlSeconds(int value) { this.workspaceTtlSeconds = value; }
    public boolean isDashboardEnabled() { return dashboardEnabled; }
    public void setDashboardEnabled(boolean value) { this.dashboardEnabled = value; }
    public int getDashboardTtlSeconds() { return dashboardTtlSeconds; }
    public void setDashboardTtlSeconds(int value) { this.dashboardTtlSeconds = value; }
    public boolean isAnalyticsEnabled() { return analyticsEnabled; }
    public void setAnalyticsEnabled(boolean value) { this.analyticsEnabled = value; }
    public int getAnalyticsTtlSeconds() { return analyticsTtlSeconds; }
    public void setAnalyticsTtlSeconds(int value) { this.analyticsTtlSeconds = value; }
    public boolean isUserDirectoryEnabled() { return userDirectoryEnabled; }
    public void setUserDirectoryEnabled(boolean value) { this.userDirectoryEnabled = value; }
    public int getUserDirectoryTtlSeconds() { return userDirectoryTtlSeconds; }
    public void setUserDirectoryTtlSeconds(int value) { this.userDirectoryTtlSeconds = value; }
    public boolean isDocumentListEnabled() { return documentListEnabled; }
    public void setDocumentListEnabled(boolean value) { this.documentListEnabled = value; }
    public int getDocumentListTtlSeconds() { return documentListTtlSeconds; }
    public void setDocumentListTtlSeconds(int value) { this.documentListTtlSeconds = value; }

    public boolean isEmbeddingEnabled() {
        return embeddingEnabled;
    }

    public void setEmbeddingEnabled(boolean embeddingEnabled) {
        this.embeddingEnabled = embeddingEnabled;
    }

    public boolean isRetrievalEnabled() {
        return retrievalEnabled;
    }

    public void setRetrievalEnabled(boolean retrievalEnabled) {
        this.retrievalEnabled = retrievalEnabled;
    }
}
