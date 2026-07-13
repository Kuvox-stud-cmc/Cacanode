package com.cacanode.api.billing.dto;

import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class UsageDto {
    private UsageDto() {
    }

    public enum AnalyticsScope {
        CUSTOMER,
        EMPLOYEE,
        ALL
    }

    public record DashboardSummary(
            long totalDocuments,
            long documentsAddedThisWeek,
            long userMessagesThisMonth,
            long userMessagesPreviousMonth,
            long storedDocumentBytes,
            long storageLimitBytes,
            long activeUsers,
            long activeUsersAddedThisWeek,
            List<RecentDocument> recentDocuments
    ) {
    }

    public record RecentDocument(
            UUID id,
            String fileName,
            DocumentType fileType,
            DocumentStatus status,
            long fileSizeBytes,
            LocalDateTime uploadedAt
    ) {
    }

    public record CountMetric(long value, long previousValue, double percentageChange) {
    }

    public record DurationMetric(
            double milliseconds,
            double previousMilliseconds,
            double percentageChange
    ) {
    }

    public record RateMetric(
            double percentage,
            double previousPercentage,
            double percentagePointChange
    ) {
    }

    public record DailyVolume(LocalDate date, long count) {
    }

    public record PopularQuestion(String question, long count) {
    }

    public record AnalyticsResponse(
            AnalyticsScope scope,
            int days,
            LocalDate periodStart,
            LocalDate periodEnd,
            CountMetric sessions,
            DurationMetric averageAssistantResponseTime,
            RateMetric closedSessionRate,
            CountMetric userMessages,
            RateMetric resolvedTicketRate,
            List<DailyVolume> dailyMessageVolume,
            List<PopularQuestion> popularQuestions
    ) {
    }
}
