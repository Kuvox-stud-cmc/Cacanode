package com.cacanode.api.analytics.query;

import com.cacanode.api.analytics.api.AnalyticsDtos;
import com.cacanode.api.analytics.api.AnalyticsReadApi;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsReadService implements AnalyticsReadApi {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final List<String> CUSTOMER_CHANNELS = List.of("WIDGET", "CUSTOM_API");
    private static final List<String> EMPLOYEE_CHANNELS = List.of("EMPLOYEE_PLAYGROUND");
    private static final List<String> ALL_CHANNELS = List.of("WIDGET", "CUSTOM_API", "EMPLOYEE_PLAYGROUND");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public AnalyticsDtos.DashboardSummary dashboardSummary(UUID tenantId) {
        LocalDate today = LocalDate.now(Clock.systemUTC());
        LocalDateTime weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = today.plusMonths(1).withDayOfMonth(1).atStartOfDay();
        LocalDateTime previousMonthStart = monthStart.minusMonths(1);

        Aggregate documents = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) AS item_count, COALESCE(SUM(file_size_bytes), 0) AS byte_count
                FROM analytics_document_projection WHERE tenant_id = ? AND deleted_at IS NULL
                """,
                (rs, rowNum) -> new Aggregate(rs.getLong("item_count"), rs.getLong("byte_count")),
                tenantId
        );
        long weeklyDocuments = count(
                "SELECT COUNT(*) FROM analytics_document_projection WHERE tenant_id = ? AND deleted_at IS NULL AND created_at >= ?",
                tenantId, weekStart
        );
        long currentMessages = count(
                "SELECT COUNT(*) FROM analytics_message_projection WHERE tenant_id = ? AND role = 'user' AND created_at >= ? AND created_at < ?",
                tenantId, monthStart, nextMonthStart
        );
        long previousMessages = count(
                "SELECT COUNT(*) FROM analytics_message_projection WHERE tenant_id = ? AND role = 'user' AND created_at >= ? AND created_at < ?",
                tenantId, previousMonthStart, monthStart
        );
        long activeUsers = count(
                "SELECT COUNT(*) FROM analytics_user_projection WHERE tenant_id = ? AND status = 'ACTIVE'",
                tenantId
        );
        long weeklyActiveUsers = count(
                "SELECT COUNT(*) FROM analytics_user_projection WHERE tenant_id = ? AND status = 'ACTIVE' AND created_at >= ?",
                tenantId, weekStart
        );
        Long maxStorageMb = jdbcTemplate.queryForObject(
                "SELECT max_storage_mb FROM analytics_tenant_projection WHERE tenant_id = ?", Long.class, tenantId
        );
        List<AnalyticsDtos.RecentDocument> recent = jdbcTemplate.query(
                """
                SELECT document_id AS id, file_name, file_type, status, file_size_bytes, created_at
                FROM analytics_document_projection
                WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 5
                """,
                (rs, rowNum) -> new AnalyticsDtos.RecentDocument(
                        rs.getObject("id", UUID.class),
                        rs.getString("file_name"),
                        AnalyticsDtos.DocumentType.valueOf(rs.getString("file_type")),
                        AnalyticsDtos.DocumentStatus.valueOf(rs.getString("status")),
                        rs.getLong("file_size_bytes"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                tenantId
        );

        Aggregate safeDocuments = documents == null ? new Aggregate(0, 0) : documents;
        return new AnalyticsDtos.DashboardSummary(
                safeDocuments.count(), weeklyDocuments, currentMessages, previousMessages,
                safeDocuments.bytes(), Math.max(0, maxStorageMb == null ? 0 : maxStorageMb) * 1024L * 1024L,
                activeUsers, weeklyActiveUsers, recent
        );
    }

    @Override
    public AnalyticsDtos.AnalyticsResponse analytics(UUID tenantId, AnalyticsDtos.AnalyticsScope scope, int days) {
        LocalDate endDate = LocalDate.now(Clock.systemUTC()).plusDays(1);
        LocalDate startDate = endDate.minusDays(days);
        LocalDate previousStartDate = startDate.minusDays(days);
        LocalDateTime previousStart = previousStartDate.atStartOfDay();
        LocalDateTime currentStart = startDate.atStartOfDay();
        LocalDateTime end = endDate.atStartOfDay();
        List<String> channels = channels(scope);
        String placeholders = String.join(",", channels.stream().map(ignored -> "?").toList());

        List<Object> periodArgs = new ArrayList<>();
        periodArgs.add(tenantId);
        periodArgs.addAll(channels);
        periodArgs.add(previousStart);
        periodArgs.add(end);

        List<SessionRow> sessions = jdbcTemplate.query(
                "SELECT created_at, status FROM analytics_conversation_projection WHERE tenant_id = ? AND channel IN (" + placeholders + ") AND created_at >= ? AND created_at < ?",
                (rs, rowNum) -> new SessionRow(rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("status")),
                periodArgs.toArray()
        );
        PeriodCounts sessionCounts = periodCounts(sessions.stream().map(SessionRow::createdAt).toList(), currentStart);
        long currentClosed = sessions.stream().filter(row -> !row.createdAt().isBefore(currentStart) && "CLOSED".equals(row.status())).count();
        long previousClosed = sessions.stream().filter(row -> row.createdAt().isBefore(currentStart) && "CLOSED".equals(row.status())).count();

        List<Object> messageArgs = new ArrayList<>();
        messageArgs.add(tenantId);
        messageArgs.addAll(channels);
        messageArgs.add(previousStart);
        messageArgs.add(end);
        List<MessageRow> messages = jdbcTemplate.query(
                """
                SELECT conversation_id AS session_id, role,
                       COALESCE(question_text, '') AS content, created_at,
                       response_duration_ms
                FROM analytics_message_projection
                WHERE tenant_id = ? AND channel IN (""" + placeholders + """
                ) AND created_at >= ? AND created_at < ? ORDER BY created_at
                """,
                (rs, rowNum) -> new MessageRow(
                        rs.getObject("session_id", UUID.class), rs.getString("role"), rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getObject("response_duration_ms", Long.class)
                ),
                messageArgs.toArray()
        );

        List<MessageRow> userMessages = messages.stream().filter(row -> "user".equals(row.role())).toList();
        PeriodCounts messageCounts = periodCounts(userMessages.stream().map(MessageRow::createdAt).toList(), currentStart);
        List<Long> currentResponseTimes = new ArrayList<>();
        List<Long> previousResponseTimes = new ArrayList<>();
        for (MessageRow message : messages) {
            if (!"assistant".equals(message.role()) || message.responseDurationMs() == null) {
                continue;
            }
            long millis = Math.max(0, message.responseDurationMs());
            (message.createdAt().isBefore(currentStart) ? previousResponseTimes : currentResponseTimes).add(millis);
        }
        double currentResponseAverage = average(currentResponseTimes);
        double previousResponseAverage = average(previousResponseTimes);

        Map<LocalDate, Long> dailyCounts = new HashMap<>();
        Map<String, QuestionGroup> questionGroups = new LinkedHashMap<>();
        for (MessageRow message : userMessages) {
            if (message.createdAt().isBefore(currentStart)) {
                continue;
            }
            dailyCounts.merge(message.createdAt().toLocalDate(), 1L, Long::sum);
            String representative = WHITESPACE.matcher(message.content().trim()).replaceAll(" ");
            if (!representative.isEmpty()) {
                String normalized = representative.toLowerCase(Locale.ROOT);
                questionGroups.compute(normalized, (key, value) -> value == null
                        ? new QuestionGroup(representative, 1)
                        : new QuestionGroup(value.representative(), value.count() + 1));
            }
        }
        List<AnalyticsDtos.DailyVolume> dailyVolume = startDate.datesUntil(endDate)
                .map(date -> new AnalyticsDtos.DailyVolume(date, dailyCounts.getOrDefault(date, 0L)))
                .toList();
        List<AnalyticsDtos.PopularQuestion> popularQuestions = questionGroups.values().stream()
                .sorted(Comparator.comparingLong(QuestionGroup::count).reversed().thenComparing(QuestionGroup::representative))
                .limit(10)
                .map(group -> new AnalyticsDtos.PopularQuestion(group.representative(), group.count()))
                .toList();

        AnalyticsDtos.RateMetric resolvedTickets = scope == AnalyticsDtos.AnalyticsScope.CUSTOMER
                ? ticketResolution(tenantId, previousStart, currentStart, end)
                : null;

        return new AnalyticsDtos.AnalyticsResponse(
                scope, days, startDate, endDate.minusDays(1),
                countMetric(sessionCounts),
                new AnalyticsDtos.DurationMetric(currentResponseAverage, previousResponseAverage, percentageChange(currentResponseAverage, previousResponseAverage)),
                rateMetric(currentClosed, sessionCounts.current(), previousClosed, sessionCounts.previous()),
                countMetric(messageCounts), resolvedTickets, dailyVolume, popularQuestions
        );
    }

    private AnalyticsDtos.RateMetric ticketResolution(UUID tenantId, LocalDateTime previousStart, LocalDateTime currentStart, LocalDateTime end) {
        List<TicketRow> tickets = jdbcTemplate.query(
                "SELECT created_at, status FROM analytics_ticket_projection WHERE tenant_id = ? AND created_at >= ? AND created_at < ?",
                (rs, rowNum) -> new TicketRow(rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("status")),
                tenantId, previousStart, end
        );
        long currentTotal = tickets.stream().filter(row -> !row.createdAt().isBefore(currentStart)).count();
        long previousTotal = tickets.size() - currentTotal;
        long currentResolved = tickets.stream().filter(row -> !row.createdAt().isBefore(currentStart) && isResolved(row.status())).count();
        long previousResolved = tickets.stream().filter(row -> row.createdAt().isBefore(currentStart) && isResolved(row.status())).count();
        return rateMetric(currentResolved, currentTotal, previousResolved, previousTotal);
    }

    private boolean isResolved(String status) {
        return "RESOLVED".equals(status) || "CLOSED".equals(status);
    }

    private AnalyticsDtos.CountMetric countMetric(PeriodCounts counts) {
        return new AnalyticsDtos.CountMetric(counts.current(), counts.previous(), percentageChange(counts.current(), counts.previous()));
    }

    private AnalyticsDtos.RateMetric rateMetric(long currentPart, long currentTotal, long previousPart, long previousTotal) {
        double current = rate(currentPart, currentTotal);
        double previous = rate(previousPart, previousTotal);
        return new AnalyticsDtos.RateMetric(current, previous, current - previous);
    }

    private PeriodCounts periodCounts(List<LocalDateTime> timestamps, LocalDateTime currentStart) {
        long current = timestamps.stream().filter(value -> !value.isBefore(currentStart)).count();
        return new PeriodCounts(current, timestamps.size() - current);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private double average(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private double percentageChange(double current, double previous) {
        return previous == 0 ? 0 : ((current - previous) / previous) * 100;
    }

    private double rate(long part, long total) {
        return total == 0 ? 0 : (part * 100.0) / total;
    }

    private List<String> channels(AnalyticsDtos.AnalyticsScope scope) {
        return switch (scope) {
            case CUSTOMER -> CUSTOMER_CHANNELS;
            case EMPLOYEE -> EMPLOYEE_CHANNELS;
            case ALL -> ALL_CHANNELS;
        };
    }

    private record Aggregate(long count, long bytes) {
    }

    private record PeriodCounts(long current, long previous) {
    }

    private record SessionRow(LocalDateTime createdAt, String status) {
    }

    private record MessageRow(UUID sessionId, String role, String content, LocalDateTime createdAt,
                              Long responseDurationMs) {
    }

    private record TicketRow(LocalDateTime createdAt, String status) {
    }

    private record QuestionGroup(String representative, long count) {
    }
}
