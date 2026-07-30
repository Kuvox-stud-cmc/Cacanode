package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.recruitment.api.RecruitmentAnalyticsExportApi;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitmentAnalyticsExportApiImpl implements RecruitmentAnalyticsExportApi {
    private final JdbcTemplate jdbc;

    @Override
    public SnapshotPage<JobStatusSnapshot> exportJobs(UUID tenantId, String cursor, int limit) {
        Cursor after = cursor(cursor); int size = size(limit);
        String sql = "SELECT id,status,created_at,updated_at,published_at,paused_at,closed_at,archived_at "
                + "FROM recruitment_jobs WHERE tenant_id=? " + after.where()
                + " ORDER BY created_at,id LIMIT ?";
        List<Object> args = after.args(tenantId, size + 1);
        List<JobStatusSnapshot> rows = jdbc.query(sql, (rs, row) -> new JobStatusSnapshot(
                rs.getObject(1, UUID.class), rs.getString(2), instant(rs.getObject(3, LocalDateTime.class)),
                instant(rs.getObject(4, LocalDateTime.class)), instantOrNull(rs.getObject(5, LocalDateTime.class)),
                instantOrNull(rs.getObject(6, LocalDateTime.class)), instantOrNull(rs.getObject(7, LocalDateTime.class)),
                instantOrNull(rs.getObject(8, LocalDateTime.class))), args.toArray());
        return page(rows, size, item -> encode(item.createdAt(), item.jobId()));
    }

    @Override
    public SnapshotPage<ApplicationStatusSnapshot> exportApplications(UUID tenantId, String cursor, int limit) {
        Cursor after = cursor(cursor); int size = size(limit);
        String sql = "SELECT id,job_id,status,created_at,updated_at,submitted_at,verified_at,withdrawn_at "
                + "FROM recruitment_applications WHERE tenant_id=? AND submitted_at IS NOT NULL " + after.where()
                + " ORDER BY created_at,id LIMIT ?";
        List<Object> args = after.args(tenantId, size + 1);
        List<ApplicationStatusSnapshot> rows = jdbc.query(sql, (rs, row) -> new ApplicationStatusSnapshot(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                instant(rs.getObject(4, LocalDateTime.class)), instant(rs.getObject(5, LocalDateTime.class)),
                instant(rs.getObject(6, LocalDateTime.class)), instantOrNull(rs.getObject(7, LocalDateTime.class)),
                instantOrNull(rs.getObject(8, LocalDateTime.class))), args.toArray());
        return page(rows, size, item -> encode(item.createdAt(), item.applicationId()));
    }

    @Override
    public SnapshotPage<InterviewStatusSnapshot> exportInterviews(UUID tenantId, String cursor, int limit) {
        Cursor after = cursor(cursor); int size = size(limit);
        String sql = "SELECT id,application_id,job_id,status,created_at,updated_at,invited_at,"
                + "scheduled_start_at,scheduled_end_at,started_at,completed_at,cancelled_at,expired_at "
                + "FROM recruitment_interviews WHERE tenant_id=? " + after.where()
                + " ORDER BY created_at,id LIMIT ?";
        List<Object> args = after.args(tenantId, size + 1);
        List<InterviewStatusSnapshot> rows = jdbc.query(sql, (rs, row) -> new InterviewStatusSnapshot(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getString(4), instant(rs.getObject(5, LocalDateTime.class)),
                instant(rs.getObject(6, LocalDateTime.class)), instantOrNull(rs.getObject(7, LocalDateTime.class)),
                rs.getObject(8, java.time.OffsetDateTime.class) == null ? null : rs.getObject(8, java.time.OffsetDateTime.class).toInstant(),
                rs.getObject(9, java.time.OffsetDateTime.class) == null ? null : rs.getObject(9, java.time.OffsetDateTime.class).toInstant(),
                instantOrNull(rs.getObject(10, LocalDateTime.class)), instantOrNull(rs.getObject(11, LocalDateTime.class)),
                instantOrNull(rs.getObject(12, LocalDateTime.class)), instantOrNull(rs.getObject(13, LocalDateTime.class))),
                args.toArray());
        return page(rows, size, item -> encode(item.createdAt(), item.interviewId()));
    }

    private static int size(int value) {
        if (value < 1 || value > 1000) throw new BadRequestException("limit must be between 1 and 1000");
        return value;
    }

    private static Cursor cursor(String value) {
        if (value == null || value.isBlank()) return new Cursor(null, null);
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            return new Cursor(LocalDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw new BadRequestException("cursor is invalid");
        }
    }

    private static String encode(java.time.Instant time, UUID id) {
        String raw = LocalDateTime.ofInstant(time, ZoneOffset.UTC) + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static java.time.Instant instant(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC); }
    private static java.time.Instant instantOrNull(LocalDateTime value) { return value == null ? null : instant(value); }

    private static <T> SnapshotPage<T> page(List<T> rows, int size, java.util.function.Function<T, String> cursor) {
        boolean more = rows.size() > size;
        List<T> items = more ? List.copyOf(rows.subList(0, size)) : List.copyOf(rows);
        return new SnapshotPage<>(items, more ? cursor.apply(items.get(items.size() - 1)) : null);
    }

    private record Cursor(LocalDateTime time, UUID id) {
        String where() { return time == null ? "" : "AND (created_at>? OR (created_at=? AND id>?))"; }
        List<Object> args(UUID tenantId, int limit) {
            return time == null ? List.of(tenantId, limit) : List.of(tenantId, time, time, id, limit);
        }
    }
}
