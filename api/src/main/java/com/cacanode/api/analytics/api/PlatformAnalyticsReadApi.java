package com.cacanode.api.analytics.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PlatformAnalyticsReadApi {
    Overview overview(int days);
    TenantPage tenants(TenantQuery query);
    TenantDetail tenant(UUID tenantId);
    Optional<TenantLabel> tenantLabel(UUID tenantId);

    record Metric(long value, long previousValue, double percentageChange) {}
    record DailyTrend(LocalDate date, long tenants, long jobs, long verifiedApplications,
                      long completedInterviews, long unsuccessfulInterviews) {}
    record Freshness(Map<String, LocalDateTime> projections) {
        public Freshness { projections = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(projections)); }
    }
    record Overview(LocalDateTime generatedAt, int days, LocalDate periodStart, LocalDate periodEnd,
                    Metric activeUsers, Metric documents, Metric storageBytes, Metric conversations,
                    Metric openTickets, Metric jobs, Metric verifiedApplications,
                    Metric completedInterviews, Metric unsuccessfulInterviews,
                    Map<String, Long> tenantStatuses, Map<String, Long> tenantPlans,
                    List<DailyTrend> trends, Freshness freshness, boolean partial, List<String> warnings) {
        public Overview {
            tenantStatuses = Map.copyOf(tenantStatuses);
            tenantPlans = Map.copyOf(tenantPlans);
            trends = List.copyOf(trends);
            warnings = List.copyOf(warnings);
        }
    }

    record TenantQuery(int page, int size, String q, String status, String plan,
                       String sort, String direction) {}
    record TenantItem(UUID tenantId, String name, String status, String plan,
                      LocalDateTime createdAt, LocalDateTime updatedAt,
                      long activeUsers, long documents, long storageBytes, long conversations,
                      long openTickets, long jobs, long verifiedApplications, long completedInterviews) {}
    record TenantPage(LocalDateTime generatedAt, List<TenantItem> items, int page, int size, long total,
                      Freshness freshness, boolean partial, List<String> warnings) {
        public TenantPage { items = List.copyOf(items); warnings = List.copyOf(warnings); }
    }
    record TenantAggregates(long totalUsers, long activeUsers, long documents, long storageBytes,
                            long userMessages, long conversations, long totalTickets, long openTickets,
                            long jobs, long totalApplications, long verifiedApplications,
                            long totalInterviews, long completedInterviews, long unsuccessfulInterviews) {}
    record TenantDetail(LocalDateTime generatedAt, UUID tenantId, String name, String status, String plan,
                        LocalDateTime createdAt, LocalDateTime updatedAt, TenantAggregates aggregates,
                        Freshness freshness, boolean partial, List<String> warnings) {
        public TenantDetail { warnings = List.copyOf(warnings); }
    }
    record TenantLabel(UUID tenantId, String name) {}
}
