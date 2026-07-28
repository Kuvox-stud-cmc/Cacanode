package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.service.PublicJobCursorCodec;
import com.cacanode.api.recruitment.service.ScreeningSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class PublicJobQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PublicJobCursorCodec cursors;
    private final Clock clock;
    private final ScreeningSupport screening;

    public PublicRecruitmentDtos.PublicJobPage search(Search request) {
        int size = request.size() == null ? 20 : request.size();
        if (size < 1 || size > 50) throw new BadRequestException("size must be between 1 and 50");
        String sort = normalizeSort(request.sort(), request.query());
        Map<String,Object> filters = request.filters(sort);
        String fingerprint = cursors.fingerprint(filters);
        PublicJobCursorCodec.Cursor cursor = request.cursor() == null || request.cursor().isBlank()
                ? null : cursors.decode(request.cursor(), sort, fingerprint);

        var params = new MapSqlParameterSource()
                .addValue("now", LocalDateTime.now(clock)).addValue("size", size + 1)
                .addValue("query", blankToNull(request.query()))
                .addValue("hasQuery", !isBlank(request.query()));
        StringBuilder where = new StringBuilder(" WHERE p.closing_at > :now");
        // Discoverability controls the global board; tenant careers pages also show unlisted
        // published jobs that are already available through their direct public URLs.
        if (isBlank(request.tenantSlug())) {
            where.append(" AND p.discoverable");
        }
        add(where, params, "p.tenant_slug=:tenantSlug", "tenantSlug", blankToNull(request.tenantSlug()));
        add(where, params, "p.department=:department", "department", blankToNull(request.department()));
        add(where, params, "p.location=:location", "location", blankToNull(request.location()));
        add(where, params, "p.employment_type=:employmentType", "employmentType", enumName(request.employmentType()));
        add(where, params, "p.work_mode=:workMode", "workMode", enumName(request.workMode()));
        add(where, params, "p.experience_level=:experienceLevel", "experienceLevel", enumName(request.experienceLevel()));
        add(where, params, "p.language=:language", "language", blankToNull(request.language()));
        if (!isBlank(request.query())) {
            where.append(" AND (p.search_vector @@ websearch_to_tsquery('simple',:query) OR ")
                    .append("(coalesce(p.title,'')||' '||coalesce(p.company_name,'')||' '||coalesce(p.department,'')||' '||coalesce(p.location,'')) % :query)");
        }
        String score = "CASE WHEN :hasQuery THEN ts_rank_cd(p.search_vector,websearch_to_tsquery('simple',:query))+greatest(similarity(p.title,:query),similarity(p.company_name,:query))*0.25 ELSE 0.0 END";
        StringBuilder outerWhere = new StringBuilder();
        String order;
        if ("relevance".equals(sort)) {
            order = " ORDER BY relevance_score DESC,public_id ASC";
            if (cursor != null) {
                params.addValue("cursorScore", Double.parseDouble(cursor.value())).addValue("cursorId", cursor.publicId());
                outerWhere.append(" WHERE relevance_score<:cursorScore OR (relevance_score=:cursorScore AND public_id>:cursorId)");
            }
        } else if ("closing_soon".equals(sort)) {
            order = " ORDER BY closing_at ASC,public_id ASC";
            if (cursor != null) {
                params.addValue("cursorTime", LocalDateTime.parse(cursor.value())).addValue("cursorId", cursor.publicId());
                outerWhere.append(" WHERE (closing_at,public_id)>(:cursorTime,:cursorId)");
            }
        } else {
            order = " ORDER BY published_at DESC,public_id DESC";
            if (cursor != null) {
                params.addValue("cursorTime", LocalDateTime.parse(cursor.value())).addValue("cursorId", cursor.publicId());
                outerWhere.append(" WHERE (published_at,public_id)<(:cursorTime,:cursorId)");
            }
        }
        String sql = "WITH ranked AS (SELECT p.*," + score + " relevance_score FROM recruitment_public_jobs p"
                + where + ") SELECT * FROM ranked" + outerWhere + order + " LIMIT :size";
        List<Row> rows = jdbc.query(sql, params, (rs, n) -> row(rs, sort));
        boolean more = rows.size() > size;
        if (more) rows = new ArrayList<>(rows.subList(0, size));
        String next = null;
        if (more && !rows.isEmpty()) {
            Row last = rows.get(rows.size() - 1);
            next = cursors.encode(sort, last.sortValue(), last.job().publicId(), fingerprint);
        }
        return new PublicRecruitmentDtos.PublicJobPage(rows.stream().map(Row::job).toList(), next);
    }

    public PublicRecruitmentDtos.PublicJob detail(UUID publicId) {
        String sql = "SELECT *,0.0 relevance_score FROM recruitment_public_jobs WHERE public_id=:publicId AND closing_at>:now";
        return jdbc.query(sql, new MapSqlParameterSource("publicId", publicId)
                        .addValue("now", LocalDateTime.now(clock)), (rs,n) -> row(rs,"newest").job())
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Public job was not found"));
    }

    private Row row(ResultSet r, String sort) throws SQLException {
        var job = new PublicRecruitmentDtos.PublicJob(
                r.getObject("public_id", UUID.class), r.getString("tenant_slug"), r.getString("company_name"),
                r.getString("title"), r.getString("description"), r.getString("description_html"), r.getString("department"), r.getString("location"),
                enumOrNull(EmploymentType.class,r.getString("employment_type")),
                enumOrNull(WorkMode.class,r.getString("work_mode")),
                enumOrNull(ExperienceLevel.class,r.getString("experience_level")),
                r.getString("language"), CvPolicy.valueOf(r.getString("cv_policy")),
                CvAiMode.valueOf(r.getString("cv_ai_mode")),r.getBoolean("cv_ai_disclosed"),
                screening.publicQuestions(r.getString("screening_questions")),
                r.getObject("published_at",LocalDateTime.class), r.getObject("closing_at",LocalDateTime.class),
                r.getBoolean("discoverable"));
        String value = switch (sort) {
            case "relevance" -> Double.toString(r.getDouble("relevance_score"));
            case "closing_soon" -> job.closingAt().toString();
            default -> job.publishedAt().toString();
        };
        return new Row(job,value);
    }

    private static String normalizeSort(String sort, String query) {
        String value = isBlank(sort) ? (isBlank(query) ? "newest" : "relevance") : sort;
        if (!Set.of("relevance","newest","closing_soon").contains(value))
            throw new BadRequestException("sort must be relevance, newest, or closing_soon");
        return value;
    }
    private static void add(StringBuilder where, MapSqlParameterSource params, String sql, String name, Object value) {
        if (value != null) { where.append(" AND ").append(sql); params.addValue(name,value); }
    }
    private static String enumName(Enum<?> value) { return value == null ? null : value.name(); }
    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
    private static String blankToNull(String value) { return isBlank(value) ? null : value.strip(); }
    private static <E extends Enum<E>> E enumOrNull(Class<E> type,String value) { return value == null ? null : Enum.valueOf(type,value); }

    private record Row(PublicRecruitmentDtos.PublicJob job, String sortValue) {}

    public record Search(String query, String tenantSlug, String department, String location,
            EmploymentType employmentType, WorkMode workMode, ExperienceLevel experienceLevel,
            String language, String sort, String cursor, Integer size) {
        Map<String,Object> filters(String normalizedSort) {
            Map<String,Object> values = new LinkedHashMap<>();
            values.put("q",blankToNull(query)); values.put("tenantSlug",blankToNull(tenantSlug));
            values.put("department",blankToNull(department)); values.put("location",blankToNull(location));
            values.put("employmentType",enumName(employmentType)); values.put("workMode",enumName(workMode));
            values.put("experienceLevel",enumName(experienceLevel)); values.put("language",blankToNull(language));
            values.put("sort",normalizedSort); values.put("size",size == null ? 20 : size);
            return values;
        }
    }
}
