package com.cacanode.api.analytics.query;

import com.cacanode.api.analytics.api.PlatformAnalyticsReadApi;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformAnalyticsReadService implements PlatformAnalyticsReadApi {
    private static final Set<Integer> ALLOWED_DAYS = Set.of(7, 30, 90);
    private static final Map<String, String> SORTS = Map.ofEntries(
            Map.entry("name", "LOWER(t.name)"), Map.entry("status", "t.status"),
            Map.entry("plan", "t.plan"), Map.entry("createdAt", "t.created_at"),
            Map.entry("updatedAt", "t.updated_at"), Map.entry("activeUsers", "active_users"),
            Map.entry("documents", "documents"), Map.entry("storageBytes", "storage_bytes"),
            Map.entry("conversations", "conversations"), Map.entry("openTickets", "open_tickets"),
            Map.entry("jobs", "jobs"), Map.entry("verifiedApplications", "verified_applications"),
            Map.entry("completedInterviews", "completed_interviews"));
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public Overview overview(int days) {
        requireDays(days);
        LocalDate endExclusive = LocalDate.now(clock).plusDays(1);
        LocalDate start = endExclusive.minusDays(days);
        LocalDate previousStart = start.minusDays(days);
        LocalDateTime currentStart = start.atStartOfDay();
        LocalDateTime end = endExclusive.atStartOfDay();
        LocalDateTime previous = previousStart.atStartOfDay();
        return new Overview(now(), days, start, endExclusive.minusDays(1),
                snapshotMetric("analytics_user_projection", "p.status='ACTIVE'", "p.created_at", currentStart),
                snapshotMetric("analytics_document_projection", "p.deleted_at IS NULL AND p.status<>'FAILED'", "p.created_at", currentStart),
                snapshotSum("analytics_document_projection", "p.file_size_bytes", "p.deleted_at IS NULL AND p.status<>'FAILED'", "p.created_at", currentStart),
                snapshotMetric("analytics_conversation_projection", "1=1", "p.created_at", currentStart),
                snapshotMetric("analytics_ticket_projection", "p.status IN ('OPEN','IN_PROGRESS')", "p.created_at", currentStart),
                snapshotMetric("analytics_recruitment_job_projection", "1=1", "p.created_at", currentStart),
                snapshotMetric("analytics_recruitment_application_projection", "p.verified_at IS NOT NULL", "p.verified_at", currentStart),
                snapshotMetric("analytics_recruitment_interview_projection", "p.status='COMPLETED'", "p.completed_at", currentStart),
                snapshotMetric("analytics_recruitment_interview_projection", "p.status IN ('FAILED','NO_ANSWER','DECLINED')", "p.updated_at", currentStart),
                distribution("status"), distribution("plan"), trends(start, endExclusive), freshness(), false, List.of());
    }

    @Override
    public TenantPage tenants(TenantQuery requested) {
        int page = Math.max(0, requested.page());
        int size = Math.min(100, Math.max(1, requested.size()));
        String q = normalize(requested.q(), 200);
        String status = normalize(requested.status(), 50);
        String plan = normalize(requested.plan(), 50);
        String sort = SORTS.getOrDefault(requested.sort(), SORTS.get("createdAt"));
        String direction = "asc".equalsIgnoreCase(requested.direction()) ? "ASC" : "DESC";
        List<Object> args = new ArrayList<>();
        String where = tenantWhere(q, status, plan, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM analytics_tenant_projection t " + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        String sql = """
                SELECT t.tenant_id,t.name,t.status,t.plan,t.created_at,t.updated_at,
                  (SELECT COUNT(*) FROM analytics_user_projection u WHERE u.tenant_id=t.tenant_id AND u.status='ACTIVE') active_users,
                  (SELECT COUNT(*) FROM analytics_document_projection d WHERE d.tenant_id=t.tenant_id AND d.deleted_at IS NULL AND d.status<>'FAILED') documents,
                  (SELECT COALESCE(SUM(d.file_size_bytes),0) FROM analytics_document_projection d WHERE d.tenant_id=t.tenant_id AND d.deleted_at IS NULL AND d.status<>'FAILED') storage_bytes,
                  (SELECT COUNT(*) FROM analytics_conversation_projection c WHERE c.tenant_id=t.tenant_id) conversations,
                  (SELECT COUNT(*) FROM analytics_ticket_projection k WHERE k.tenant_id=t.tenant_id AND k.status IN ('OPEN','IN_PROGRESS')) open_tickets,
                  (SELECT COUNT(*) FROM analytics_recruitment_job_projection j WHERE j.tenant_id=t.tenant_id) jobs,
                  (SELECT COUNT(*) FROM analytics_recruitment_application_projection a WHERE a.tenant_id=t.tenant_id AND a.verified_at IS NOT NULL) verified_applications,
                  (SELECT COUNT(*) FROM analytics_recruitment_interview_projection i WHERE i.tenant_id=t.tenant_id AND i.status='COMPLETED') completed_interviews
                FROM analytics_tenant_projection t
                """ + where + " ORDER BY " + sort + " " + direction + ", t.tenant_id ASC LIMIT ? OFFSET ?";
        List<TenantItem> items = jdbc.query(sql, (rs, row) -> new TenantItem(
                rs.getObject("tenant_id", UUID.class), rs.getString("name"), rs.getString("status"),
                rs.getString("plan"), time(rs.getTimestamp("created_at")), time(rs.getTimestamp("updated_at")),
                rs.getLong("active_users"), rs.getLong("documents"), rs.getLong("storage_bytes"),
                rs.getLong("conversations"), rs.getLong("open_tickets"), rs.getLong("jobs"),
                rs.getLong("verified_applications"), rs.getLong("completed_interviews")), pageArgs.toArray());
        return new TenantPage(now(), items, page, size, total == null ? 0 : total, freshness(), false, List.of());
    }

    @Override
    public TenantDetail tenant(UUID tenantId) {
        var metadata = jdbc.query("""
                SELECT tenant_id,name,status,plan,created_at,updated_at
                FROM analytics_tenant_projection WHERE tenant_id=? AND tenant_kind='CUSTOMER'
                """, (rs, row) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getString(4), time(rs.getTimestamp(5)), time(rs.getTimestamp(6))}, tenantId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Customer tenant was not found"));
        TenantAggregates aggregates = jdbc.queryForObject("""
                SELECT
                 (SELECT COUNT(*) FROM analytics_user_projection WHERE tenant_id=?) total_users,
                 (SELECT COUNT(*) FROM analytics_user_projection WHERE tenant_id=? AND status='ACTIVE') active_users,
                 (SELECT COUNT(*) FROM analytics_document_projection WHERE tenant_id=? AND deleted_at IS NULL AND status<>'FAILED') documents,
                 (SELECT COALESCE(SUM(file_size_bytes),0) FROM analytics_document_projection WHERE tenant_id=? AND deleted_at IS NULL AND status<>'FAILED') storage_bytes,
                 (SELECT COUNT(*) FROM analytics_message_projection WHERE tenant_id=? AND role='user') user_messages,
                 (SELECT COUNT(*) FROM analytics_conversation_projection WHERE tenant_id=?) conversations,
                 (SELECT COUNT(*) FROM analytics_ticket_projection WHERE tenant_id=?) total_tickets,
                 (SELECT COUNT(*) FROM analytics_ticket_projection WHERE tenant_id=? AND status IN ('OPEN','IN_PROGRESS')) open_tickets,
                 (SELECT COUNT(*) FROM analytics_recruitment_job_projection WHERE tenant_id=?) jobs,
                 (SELECT COUNT(*) FROM analytics_recruitment_application_projection WHERE tenant_id=?) total_applications,
                 (SELECT COUNT(*) FROM analytics_recruitment_application_projection WHERE tenant_id=? AND verified_at IS NOT NULL) verified_applications,
                 (SELECT COUNT(*) FROM analytics_recruitment_interview_projection WHERE tenant_id=?) total_interviews,
                 (SELECT COUNT(*) FROM analytics_recruitment_interview_projection WHERE tenant_id=? AND status='COMPLETED') completed_interviews,
                 (SELECT COUNT(*) FROM analytics_recruitment_interview_projection WHERE tenant_id=? AND status IN ('FAILED','NO_ANSWER','DECLINED')) unsuccessful_interviews
                """, (rs, row) -> new TenantAggregates(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8),
                rs.getLong(9), rs.getLong(10), rs.getLong(11), rs.getLong(12), rs.getLong(13), rs.getLong(14)),
                repeat(tenantId, 14));
        return new TenantDetail(now(), tenantId, (String) metadata[1], (String) metadata[2], (String) metadata[3],
                (LocalDateTime) metadata[4], (LocalDateTime) metadata[5], aggregates, freshness(), false, List.of());
    }

    @Override
    public Optional<TenantLabel> tenantLabel(UUID tenantId) {
        return jdbc.query("SELECT tenant_id,name FROM analytics_tenant_projection WHERE tenant_id=? AND tenant_kind='CUSTOMER'",
                (rs, row) -> new TenantLabel(rs.getObject(1, UUID.class), rs.getString(2)), tenantId).stream().findFirst();
    }

    private Metric snapshotMetric(String table, String condition, String previousTimestamp, LocalDateTime currentStart) {
        long value = count("SELECT COUNT(*) FROM " + table + " p JOIN analytics_tenant_projection t ON t.tenant_id=p.tenant_id AND t.tenant_kind='CUSTOMER' WHERE " + condition);
        long previous = count("SELECT COUNT(*) FROM " + table + " p JOIN analytics_tenant_projection t ON t.tenant_id=p.tenant_id AND t.tenant_kind='CUSTOMER' WHERE " + condition + " AND " + previousTimestamp + "<?", currentStart);
        return new Metric(value, previous, percentageChange(value, previous));
    }

    private Metric snapshotSum(String table, String column, String condition, String previousTimestamp, LocalDateTime currentStart) {
        long value = count("SELECT COALESCE(SUM(" + column + "),0) FROM " + table + " p JOIN analytics_tenant_projection t ON t.tenant_id=p.tenant_id AND t.tenant_kind='CUSTOMER' WHERE " + condition);
        long previous = count("SELECT COALESCE(SUM(" + column + "),0) FROM " + table + " p JOIN analytics_tenant_projection t ON t.tenant_id=p.tenant_id AND t.tenant_kind='CUSTOMER' WHERE " + condition + " AND " + previousTimestamp + "<?", currentStart);
        return new Metric(value, previous, percentageChange(value, previous));
    }

    private List<DailyTrend> trends(LocalDate start, LocalDate end) {
        Map<LocalDate, long[]> values = new HashMap<>();
        trend(values, 0, "analytics_tenant_projection p", "p.created_at", "p.tenant_kind='CUSTOMER'", start, end);
        trend(values, 1, "analytics_recruitment_job_projection p JOIN analytics_tenant_projection t ON t.tenant_id=p.tenant_id", "p.published_at", "t.tenant_kind='CUSTOMER' AND p.published_at IS NOT NULL", start, end);
        trend(values, 2, "analytics_recruitment_application_projection p JOIN analytics_tenant_projection t ON t.tenant_id=p.tenant_id", "p.verified_at", "t.tenant_kind='CUSTOMER' AND p.verified_at IS NOT NULL", start, end);
        trend(values, 3, "analytics_recruitment_interview_projection p JOIN analytics_tenant_projection t ON t.tenant_id=p.tenant_id", "p.completed_at", "t.tenant_kind='CUSTOMER' AND p.status='COMPLETED' AND p.completed_at IS NOT NULL", start, end);
        trend(values, 4, "analytics_recruitment_interview_projection p JOIN analytics_tenant_projection t ON t.tenant_id=p.tenant_id", "p.updated_at", "t.tenant_kind='CUSTOMER' AND p.status IN ('FAILED','NO_ANSWER','DECLINED')", start, end);
        return start.datesUntil(end).map(date -> {
            long[] row = values.getOrDefault(date, new long[5]);
            return new DailyTrend(date, row[0], row[1], row[2], row[3], row[4]);
        }).toList();
    }

    private void trend(Map<LocalDate, long[]> values, int index, String from, String timestamp,
                       String condition, LocalDate start, LocalDate end) {
        jdbc.query("SELECT CAST(" + timestamp + " AS DATE),COUNT(*) FROM " + from + " WHERE " + condition
                        + " AND " + timestamp + ">=? AND " + timestamp + "<? GROUP BY CAST(" + timestamp + " AS DATE)",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> values.computeIfAbsent(rs.getDate(1).toLocalDate(), ignored -> new long[5])[index] = rs.getLong(2),
                start.atStartOfDay(), end.atStartOfDay());
    }

    private Map<String, Long> distribution(String column) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query("SELECT " + column + ",COUNT(*) FROM analytics_tenant_projection WHERE tenant_kind='CUSTOMER' GROUP BY " + column + " ORDER BY " + column,
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(rs.getString(1), rs.getLong(2)));
        return result;
    }

    private Freshness freshness() {
        Map<String, String> tables = new LinkedHashMap<>();
        tables.put("tenants", "analytics_tenant_projection:updated_at");
        tables.put("users", "analytics_user_projection:updated_at");
        tables.put("documents", "analytics_document_projection:updated_at");
        tables.put("conversations", "analytics_conversation_projection:updated_at");
        tables.put("messages", "analytics_message_projection:created_at");
        tables.put("tickets", "analytics_ticket_projection:updated_at");
        tables.put("jobs", "analytics_recruitment_job_projection:updated_at");
        tables.put("applications", "analytics_recruitment_application_projection:updated_at");
        tables.put("interviews", "analytics_recruitment_interview_projection:updated_at");
        Map<String, LocalDateTime> values = new LinkedHashMap<>();
        tables.forEach((name, descriptor) -> {
            String[] parts = descriptor.split(":");
            Timestamp value = jdbc.queryForObject("SELECT MAX(p." + parts[1] + ") FROM " + parts[0]
                    + " p JOIN analytics_tenant_projection t ON t.tenant_id=p.tenant_id AND t.tenant_kind='CUSTOMER'", Timestamp.class);
            values.put(name, time(value));
        });
        return new Freshness(values);
    }

    private String tenantWhere(String q, String status, String plan, List<Object> args) {
        StringBuilder value = new StringBuilder(" WHERE t.tenant_kind='CUSTOMER'");
        if (!q.isBlank()) { value.append(" AND (LOWER(t.name) LIKE ? OR CAST(t.tenant_id AS VARCHAR) LIKE ?)"); args.add("%" + q.toLowerCase(Locale.ROOT) + "%"); args.add("%" + q + "%"); }
        if (!status.isBlank()) { value.append(" AND t.status=?"); args.add(status.toUpperCase(Locale.ROOT)); }
        if (!plan.isBlank()) { value.append(" AND t.plan=?"); args.add(plan.toUpperCase(Locale.ROOT)); }
        return value.toString();
    }

    private long count(String sql, Object... args) { Long value = jdbc.queryForObject(sql, Long.class, args); return value == null ? 0 : value; }
    private double percentageChange(long current, long previous) { return previous == 0 ? 0 : (current - previous) * 100.0 / previous; }
    private void requireDays(int days) { if (!ALLOWED_DAYS.contains(days)) throw new IllegalArgumentException("days must be 7, 30, or 90"); }
    private String normalize(String value, int limit) { String normalized = value == null ? "" : value.trim(); return normalized.length() <= limit ? normalized : normalized.substring(0, limit); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private static Object[] repeat(Object value, int count) { Object[] values = new Object[count]; java.util.Arrays.fill(values, value); return values; }
}
