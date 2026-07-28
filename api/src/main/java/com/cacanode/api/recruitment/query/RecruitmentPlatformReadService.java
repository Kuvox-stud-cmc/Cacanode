package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.recruitment.api.RecruitmentPlatformReadApi;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(prefix = "app.recruitment", name = "enabled", havingValue = "true")
public class RecruitmentPlatformReadService implements RecruitmentPlatformReadApi {
    private static final String VISIBLE = "(:publicJobsEnabled AND p.job_id IS NOT NULL AND p.discoverable "
            + "AND j.status='PUBLISHED' AND j.closing_at>:now)";

    private final NamedParameterJdbcTemplate jdbc;
    private final RecruitmentProperties recruitmentProperties;
    private final Clock clock;

    @Override
    public JobPage jobs(JobQuery query) {
        MapSqlParameterSource params = baseParameters();
        String where = where(query, params);
        long total = jdbc.queryForObject("SELECT count(*) FROM recruitment_jobs j "
                + "LEFT JOIN recruitment_public_jobs p ON p.tenant_id=j.tenant_id AND p.job_id=j.id "
                + where, params, Long.class);

        params.addValue("limit", query.size()).addValue("offset", (long) query.page() * query.size());
        String sql = """
                SELECT j.id,j.public_id,j.tenant_id,j.frozen_company_name,j.title,j.status,
                       j.department,j.location,j.language,j.employment_type,j.work_mode,j.experience_level,
                       j.published_at,j.closing_at,j.updated_at,coalesce(p.discoverable,false) discoverable,
                       %s visible_on_public_board,
                       coalesce(a.total,0) total_applications,coalesce(i.total,0) total_interviews
                FROM recruitment_jobs j
                LEFT JOIN recruitment_public_jobs p ON p.tenant_id=j.tenant_id AND p.job_id=j.id
                LEFT JOIN LATERAL (
                    SELECT count(*) total FROM recruitment_applications a
                    WHERE a.tenant_id=j.tenant_id AND a.job_id=j.id
                ) a ON true
                LEFT JOIN LATERAL (
                    SELECT count(*) total FROM recruitment_interviews i
                    WHERE i.tenant_id=j.tenant_id AND i.job_id=j.id
                ) i ON true
                %s
                %s
                LIMIT :limit OFFSET :offset
                """.formatted(VISIBLE, where, order(query.sort(), query.direction()));
        List<JobItem> items = jdbc.query(sql, params, (rs, rowNum) -> item(rs));
        return new JobPage(items, query.page(), query.size(), total);
    }

    @Override
    public JobDetail job(UUID jobId) {
        MapSqlParameterSource params = baseParameters().addValue("jobId", jobId);
        String sql = """
                SELECT j.id,j.public_id,j.tenant_id,j.frozen_company_name,j.title,j.status,
                       j.department,j.location,j.language,j.employment_type,j.work_mode,j.experience_level,
                       j.published_at,j.closing_at,j.updated_at,coalesce(p.discoverable,false) discoverable,
                       %s visible_on_public_board,
                       coalesce(a.total,0) total_applications,coalesce(a.verified,0) verified_applications,
                       coalesce(i.total,0) total_interviews,coalesce(i.completed,0) completed_interviews,
                       coalesce(i.unsuccessful,0) unsuccessful_interviews
                FROM recruitment_jobs j
                LEFT JOIN recruitment_public_jobs p ON p.tenant_id=j.tenant_id AND p.job_id=j.id
                LEFT JOIN LATERAL (
                    SELECT count(*) total,count(*) FILTER (WHERE a.verified_at IS NOT NULL) verified
                    FROM recruitment_applications a WHERE a.tenant_id=j.tenant_id AND a.job_id=j.id
                ) a ON true
                LEFT JOIN LATERAL (
                    SELECT count(*) total,count(*) FILTER (WHERE i.status='COMPLETED') completed,
                           count(*) FILTER (WHERE i.status IN ('FAILED','NO_ANSWER','DECLINED')) unsuccessful
                    FROM recruitment_interviews i WHERE i.tenant_id=j.tenant_id AND i.job_id=j.id
                ) i ON true
                WHERE j.id=:jobId
                """.formatted(VISIBLE);
        return jdbc.query(sql, params, (rs, rowNum) -> detail(rs)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Recruitment job not found"));
    }

    private MapSqlParameterSource baseParameters() {
        return new MapSqlParameterSource()
                .addValue("now", utc(clock.instant()))
                .addValue("publicJobsEnabled", recruitmentProperties.publicJobsEnabled());
    }

    private String where(JobQuery query, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        add(clauses, params, query.tenantId(), "tenantId", "j.tenant_id=:tenantId");
        addEnum(clauses, params, query.status(), "status", "j.status=:status");
        add(clauses, params, trim(query.language()), "language", "j.language=:language");
        add(clauses, params, trim(query.department()), "department", "j.department=:department");
        add(clauses, params, trim(query.location()), "location", "j.location=:location");
        addEnum(clauses, params, query.employmentType(), "employmentType", "j.employment_type=:employmentType");
        addEnum(clauses, params, query.workMode(), "workMode", "j.work_mode=:workMode");
        addTime(clauses, params, query.closingFrom(), "closingFrom", "j.closing_at>=:closingFrom");
        addTime(clauses, params, query.closingTo(), "closingTo", "j.closing_at<:closingTo");
        addTime(clauses, params, query.updatedFrom(), "updatedFrom", "j.updated_at>=:updatedFrom");
        addTime(clauses, params, query.updatedTo(), "updatedTo", "j.updated_at<:updatedTo");
        if (query.visibility() != null) {
            clauses.add(query.visibility() == Visibility.VISIBLE ? VISIBLE : "NOT " + VISIBLE);
        }
        String search = trim(query.search());
        if (search != null) {
            params.addValue("search", "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%");
            clauses.add("(lower(coalesce(j.title,'')||' '||coalesce(j.frozen_company_name,'')||' '"
                    + "||coalesce(j.department,'')||' '||coalesce(j.location,'')) LIKE :search ESCAPE '\\' "
                    + "OR lower(cast(j.id as text)) LIKE :search ESCAPE '\\' "
                    + "OR lower(cast(j.public_id as text)) LIKE :search ESCAPE '\\')");
        }
        return clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
    }

    private static String order(Sort sort, Direction direction) {
        String dir = direction == Direction.ASC ? "ASC" : "DESC";
        String value = switch (sort) {
            case TITLE -> "lower(j.title)";
            case COMPANY_NAME -> "lower(j.frozen_company_name)";
            case STATUS -> "j.status";
            case PUBLISHED_AT -> "j.published_at";
            case CLOSING_AT -> "j.closing_at";
            case UPDATED_AT -> "j.updated_at";
            case APPLICATIONS -> "coalesce(a.total,0)";
            case INTERVIEWS -> "coalesce(i.total,0)";
            case VISIBILITY -> VISIBLE;
        };
        boolean nullableTimestamp = sort == Sort.PUBLISHED_AT || sort == Sort.CLOSING_AT;
        return "ORDER BY " + (nullableTimestamp ? value + " IS NULL ASC," : "")
                + value + " " + dir + ",j.id ASC";
    }

    private static JobItem item(ResultSet rs) throws SQLException {
        return new JobItem(uuid(rs, "id"), uuid(rs, "public_id"), uuid(rs, "tenant_id"),
                rs.getString("frozen_company_name"), rs.getString("title"), enumValue(JobStatus.class, rs, "status"),
                rs.getString("department"), rs.getString("location"), rs.getString("language"),
                enumValue(EmploymentType.class, rs, "employment_type"), enumValue(WorkMode.class, rs, "work_mode"),
                enumValue(ExperienceLevel.class, rs, "experience_level"), instant(rs, "published_at"),
                instant(rs, "closing_at"), instant(rs, "updated_at"), rs.getBoolean("discoverable"),
                rs.getBoolean("visible_on_public_board"), rs.getLong("total_applications"),
                rs.getLong("total_interviews"));
    }

    private static JobDetail detail(ResultSet rs) throws SQLException {
        JobItem item = item(rs);
        return new JobDetail(item.jobId(), item.publicId(), item.tenantId(), item.frozenCompanyName(), item.title(),
                item.status(), item.department(), item.location(), item.language(), item.employmentType(), item.workMode(),
                item.experienceLevel(), item.publishedAt(), item.closingAt(), item.updatedAt(), item.discoverable(),
                item.visibleOnPublicBoard(), item.totalApplications(), rs.getLong("verified_applications"),
                item.totalInterviews(), rs.getLong("completed_interviews"), rs.getLong("unsuccessful_interviews"));
    }

    private static void add(List<String> clauses, MapSqlParameterSource params, Object value, String name, String sql) {
        if (value != null) { params.addValue(name, value); clauses.add(sql); }
    }

    private static void addEnum(List<String> clauses, MapSqlParameterSource params, Enum<?> value, String name, String sql) {
        add(clauses, params, value == null ? null : value.name(), name, sql);
    }

    private static void addTime(List<String> clauses, MapSqlParameterSource params, Instant value, String name, String sql) {
        add(clauses, params, value == null ? null : utc(value), name, sql);
    }

    private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
    private static UUID uuid(ResultSet rs, String column) throws SQLException { return rs.getObject(column, UUID.class); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
    private static <E extends Enum<E>> E enumValue(Class<E> type, ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }
}
