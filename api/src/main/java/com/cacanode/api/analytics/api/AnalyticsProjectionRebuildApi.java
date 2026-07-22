package com.cacanode.api.analytics.api;

public interface AnalyticsProjectionRebuildApi {
    RebuildResult rebuild();

    record RebuildResult(long tenants, long users, long invitations, long documents,
                         long conversations, long messages, long tickets) {}
}
