package com.cacanode.api.common.service;

import com.cacanode.api.common.api.operations.OperationalFailureReadApi;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OperationalFailureQueryExecutor {
    private final JdbcTemplate jdbc;

    public OperationalFailureReadApi.Summary summary(String normalizedSql, List<?> baseArgs,
                                                       OperationalFailureReadApi.Source source,
                                                       Optional<UUID> tenantId) {
        Filter filter = filter(baseArgs, tenantId, null, null);
        String sql = "WITH failures AS (" + normalizedSql + ") SELECT state,severity,COUNT(*) total FROM failures"
                + filter.where() + " GROUP BY state,severity";
        EnumMap<OperationalFailureReadApi.State, Long> states = new EnumMap<>(OperationalFailureReadApi.State.class);
        EnumMap<OperationalFailureReadApi.Severity, Long> severities = new EnumMap<>(OperationalFailureReadApi.Severity.class);
        long[] total = {0};
        jdbc.query(sql, rs -> {
            long count = rs.getLong("total");
            total[0] += count;
            states.merge(OperationalFailureReadApi.State.valueOf(rs.getString("state")), count, Long::sum);
            severities.merge(OperationalFailureReadApi.Severity.valueOf(rs.getString("severity")), count, Long::sum);
        }, filter.args().toArray());
        return new OperationalFailureReadApi.Summary(total[0], states, severities);
    }

    public OperationalFailureReadApi.Page page(String normalizedSql, List<?> baseArgs,
                                                 OperationalFailureReadApi.Source source,
                                                 OperationalFailureReadApi.Query query) {
        Filter filter = filter(baseArgs, query.tenantId(), query.state(), query.severity());
        List<Object> args = new ArrayList<>(filter.args());
        args.add(query.size());
        args.add((long) query.page() * query.size());
        String sql = "WITH failures AS (" + normalizedSql + "), filtered AS (SELECT * FROM failures"
                + filter.where() + ") SELECT *,COUNT(*) OVER() total_count FROM filtered ORDER BY "
                + sort(query.sort()) + ("asc".equalsIgnoreCase(query.direction()) ? " ASC" : " DESC")
                + ", failure_id ASC LIMIT ? OFFSET ?";
        List<OperationalFailureReadApi.Failure> items = new ArrayList<>();
        long[] total = {0};
        jdbc.query(sql, rs -> {
            total[0] = rs.getLong("total_count");
            items.add(map(source, rs));
        }, args.toArray());
        if (items.isEmpty()) {
            String countSql = "WITH failures AS (" + normalizedSql + ") SELECT COUNT(*) FROM failures" + filter.where();
            Long count = jdbc.queryForObject(countSql, Long.class, filter.args().toArray());
            total[0] = count == null ? 0 : count;
        }
        return new OperationalFailureReadApi.Page(items, total[0]);
    }

    public List<OperationalFailureReadApi.Failure> recent(String normalizedSql, List<?> baseArgs,
                                                           OperationalFailureReadApi.Source source,
                                                           Optional<UUID> tenantId, int limit) {
        Filter filter = filter(baseArgs, tenantId, null, null);
        List<Object> args = new ArrayList<>(filter.args());
        args.add(Math.max(0, limit));
        String sql = "WITH failures AS (" + normalizedSql + ") SELECT * FROM failures" + filter.where()
                + " ORDER BY last_seen_at DESC NULLS LAST,failure_id ASC LIMIT ?";
        return jdbc.query(sql, (rs, row) -> map(source, rs), args.toArray());
    }

    private Filter filter(List<?> baseArgs, Optional<UUID> tenantId, OperationalFailureReadApi.State state,
                          OperationalFailureReadApi.Severity severity) {
        List<Object> args = new ArrayList<>(baseArgs);
        List<String> clauses = new ArrayList<>();
        tenantId.ifPresent(value -> { clauses.add("tenant_id=?"); args.add(value); });
        if (state != null) { clauses.add("state=?"); args.add(state.name()); }
        if (severity != null) { clauses.add("severity=?"); args.add(severity.name()); }
        return new Filter(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), args);
    }

    private String sort(String value) {
        return switch (value) {
            case "firstSeenAt" -> "first_seen_at";
            case "attempts" -> "attempts";
            case "severity" -> "CASE severity WHEN 'WARNING' THEN 0 WHEN 'ERROR' THEN 1 WHEN 'CRITICAL' THEN 2 END";
            case "state" -> "CASE state WHEN 'RETRYING' THEN 0 WHEN 'FAILED' THEN 1 WHEN 'DEAD' THEN 2 "
                    + "WHEN 'REVIEW' THEN 3 WHEN 'STALLED' THEN 4 END";
            default -> "last_seen_at";
        };
    }

    private OperationalFailureReadApi.Failure map(OperationalFailureReadApi.Source source, ResultSet rs)
            throws SQLException {
        return new OperationalFailureReadApi.Failure(source, rs.getObject("failure_id", UUID.class),
                rs.getObject("tenant_id", UUID.class), rs.getObject("resource_id", UUID.class),
                OperationalFailureReadApi.ResourceType.valueOf(rs.getString("resource_type")),
                OperationalFailureReadApi.State.valueOf(rs.getString("state")),
                OperationalFailureReadApi.Severity.valueOf(rs.getString("severity")),
                OperationalFailureReadApi.Code.valueOf(rs.getString("error_code")), rs.getInt("attempts"),
                time(rs.getTimestamp("first_seen_at")), time(rs.getTimestamp("last_seen_at")),
                time(rs.getTimestamp("next_retry_at")));
    }

    private static LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private record Filter(String where, List<Object> args) {}
}
