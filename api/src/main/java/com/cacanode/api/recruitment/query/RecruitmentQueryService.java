package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.service.ScreeningSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.recruitment", name = "enabled", havingValue = "true")
public class RecruitmentQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    @Autowired(required=false) private ScreeningSupport screening;
    @Autowired(required=false) private ObjectMapper objectMapper;

    public RecruitmentDtos.PageResult<RecruitmentDtos.JobResponse> jobs(UUID tenantId, int page, int size,
            JobStatus status, String department, String location, String employmentType, String workMode,
            String language, LocalDateTime closingFrom, LocalDateTime closingTo, String search,
            String sort, String direction) {
        Page p = page(page, size); Params params = new Params(tenantId, p);
        StringBuilder where = base("j");
        add(where, params, "j.status = :status", "status", status);
        add(where, params, "j.department = :department", "department", department);
        add(where, params, "j.location = :location", "location", location);
        add(where, params, "j.employment_type = :employmentType", "employmentType", employmentType);
        add(where, params, "j.work_mode = :workMode", "workMode", workMode);
        add(where, params, "j.language = :language", "language", language);
        range(where, params, "j.closing_at", closingFrom, closingTo);
        search(where, params, search, "concat_ws(' ', j.title, j.department, j.location)");
        String from = " FROM recruitment_jobs j " + where;
        String order = order(sort, direction, Map.of(
                "createdAt","j.created_at", "updatedAt","j.updated_at", "title","j.title",
                "status","j.status", "closingAt","j.closing_at", "publishedAt","j.published_at"), "createdAt");
        List<RecruitmentDtos.JobResponse> items = jdbc.query("SELECT j.*" + from + order + limit(), params.values, JOB);
        return result(items, from, params);
    }

    public RecruitmentDtos.PageResult<RecruitmentDtos.TemplateResponse> templates(UUID tenantId, int page, int size,
            String locale, Boolean archived, String search, String sort, String direction) {
        Page p = page(page,size); Params params = new Params(tenantId,p); StringBuilder where = base("t");
        add(where, params, "t.locale = :locale", "locale", locale);
        add(where, params, "t.archived = :archived", "archived", archived);
        search(where, params, search, "t.name");
        String from = " FROM recruitment_interview_templates t " + where;
        String order = order(sort,direction,Map.of("createdAt","t.created_at","updatedAt","t.updated_at",
                "name","t.name","archived","t.archived"),"createdAt");
        String select = "SELECT t.*, COALESCE((SELECT max(r.revision_number) FROM recruitment_interview_template_revisions r WHERE r.tenant_id=t.tenant_id AND r.template_id=t.id),0) latest_revision_number";
        List<RecruitmentDtos.TemplateResponse> items=jdbc.query(select+from+order+limit(),params.values,TEMPLATE);
        return result(items,from,params);
    }

    public RecruitmentDtos.PageResult<RecruitmentDtos.CandidateResponse> candidates(UUID tenantId, int page, int size,
            UUID jobId, String search, String sort, String direction) {
        Page p=page(page,size); Params params=new Params(tenantId,p); StringBuilder where=base("c");
        if(jobId!=null){where.append(" AND EXISTS (SELECT 1 FROM recruitment_applications a WHERE a.tenant_id=c.tenant_id AND a.candidate_id=c.id AND a.job_id=:jobId)");params.values.addValue("jobId",jobId);}
        search(where,params,search,"concat_ws(' ', c.normalized_name, c.normalized_email, c.phone)");
        String from=" FROM recruitment_candidates c "+where;
        String order=order(sort,direction,Map.of("createdAt","c.created_at","updatedAt","c.updated_at","name","c.normalized_name","email","c.normalized_email"),"createdAt");
        List<RecruitmentDtos.CandidateResponse> items=jdbc.query("SELECT c.*"+from+order+limit(),params.values,CANDIDATE);
        return result(items,from,params);
    }

    public RecruitmentDtos.PageResult<RecruitmentDtos.ApplicationResponse> applications(UUID tenantId,int page,int size,
            ApplicationStatus status,UUID jobId,UUID candidateId,LocalDateTime submittedFrom,LocalDateTime submittedTo,
            Boolean cvPresent,CvAnalysisStatus cvAnalysisStatus,InterviewStatus interviewStatus,
            BigDecimal scoreMin,BigDecimal scoreMax,String englishBand,String search,String sort,String direction){
        scoreRange(scoreMin,scoreMax); Page p=page(page,size); Params params=new Params(tenantId,p); StringBuilder where=base("a");
        add(where,params,"a.status = :status","status",status); add(where,params,"a.job_id = :jobId","jobId",jobId);
        add(where,params,"a.candidate_id = :candidateId","candidateId",candidateId);
        range(where,params,"a.submitted_at",submittedFrom,submittedTo); add(where,params,"a.cv_present = :cvPresent","cvPresent",cvPresent);
        add(where,params,"a.cv_analysis_status = :cvAnalysisStatus","cvAnalysisStatus",cvAnalysisStatus);
        add(where,params,"i.status = :interviewStatus","interviewStatus",interviewStatus);
        lowerUpper(where,params,"a.overall_score",scoreMin,scoreMax); add(where,params,"a.english_band = :englishBand","englishBand",englishBand);
        search(where,params,search,"concat_ws(' ', c.normalized_name, c.normalized_email)");
        String joins=" FROM recruitment_applications a JOIN recruitment_jobs j ON j.tenant_id=a.tenant_id AND j.id=a.job_id JOIN recruitment_candidates c ON c.tenant_id=a.tenant_id AND c.id=a.candidate_id LEFT JOIN recruitment_interviews i ON i.tenant_id=a.tenant_id AND i.application_id=a.id ";
        String from=joins+where;
        String order=order(sort,direction,Map.of("submittedAt","a.submitted_at","createdAt","a.created_at","updatedAt","a.updated_at","status","a.status","score","a.overall_score","candidateName","c.normalized_name"),"submittedAt");
        String select="SELECT a.*, j.title job_title, c.full_name candidate_name, c.email candidate_email, i.status interview_status";
        List<RecruitmentDtos.ApplicationResponse> items=jdbc.query(select+from+order+limit(),params.values,APPLICATION);
        return result(items,from,params);
    }

    public RecruitmentDtos.PageResult<RecruitmentDtos.InterviewResponse> interviews(UUID tenantId,int page,int size,
            InterviewStatus status,UUID jobId,UUID applicationId,LocalDateTime dateFrom,LocalDateTime dateTo,
            BigDecimal scoreMin,BigDecimal scoreMax,String englishBand,String search,String sort,String direction){
        scoreRange(scoreMin,scoreMax); Page p=page(page,size); Params params=new Params(tenantId,p); StringBuilder where=base("i");
        add(where,params,"i.status = :status","status",status); add(where,params,"i.job_id = :jobId","jobId",jobId);
        add(where,params,"i.application_id = :applicationId","applicationId",applicationId); range(where,params,"i.scheduled_at",dateFrom,dateTo);
        lowerUpper(where,params,"i.overall_score",scoreMin,scoreMax); add(where,params,"i.english_band = :englishBand","englishBand",englishBand);
        search(where,params,search,"concat_ws(' ', c.normalized_name, c.normalized_email)");
        String from=" FROM recruitment_interviews i JOIN recruitment_applications a ON a.tenant_id=i.tenant_id AND a.id=i.application_id JOIN recruitment_candidates c ON c.tenant_id=a.tenant_id AND c.id=a.candidate_id JOIN recruitment_jobs j ON j.tenant_id=i.tenant_id AND j.id=i.job_id "+where;
        String order=order(sort,direction,Map.of("scheduledAt","i.scheduled_at","createdAt","i.created_at","updatedAt","i.updated_at","status","i.status","score","i.overall_score","completedAt","i.completed_at","candidateName","c.normalized_name"),"createdAt");
        String select="SELECT i.*, j.title job_title, c.id candidate_id, c.full_name candidate_name";
        List<RecruitmentDtos.InterviewResponse> items=jdbc.query(select+from+order+limit(),params.values,INTERVIEW);
        return result(items,from,params);
    }

    public RecruitmentDtos.ApplicationResponse application(UUID tenantId,UUID id){
        Params params=new Params(tenantId,new Page(0,1)); params.values.addValue("id",id);
        String sql="SELECT a.*, j.title job_title, c.full_name candidate_name, c.email candidate_email, i.status interview_status FROM recruitment_applications a JOIN recruitment_jobs j ON j.tenant_id=a.tenant_id AND j.id=a.job_id JOIN recruitment_candidates c ON c.tenant_id=a.tenant_id AND c.id=a.candidate_id LEFT JOIN recruitment_interviews i ON i.tenant_id=a.tenant_id AND i.application_id=a.id WHERE a.tenant_id=:tenantId AND a.id=:id";
        return jdbc.query(sql,params.values,APPLICATION).stream().findFirst().orElseThrow(()->notFound("Application"));
    }
    public RecruitmentDtos.InterviewResponse interview(UUID tenantId,UUID id){
        Params params=new Params(tenantId,new Page(0,1)); params.values.addValue("id",id);
        String sql="SELECT i.*, j.title job_title, c.id candidate_id, c.full_name candidate_name FROM recruitment_interviews i JOIN recruitment_applications a ON a.tenant_id=i.tenant_id AND a.id=i.application_id JOIN recruitment_candidates c ON c.tenant_id=a.tenant_id AND c.id=a.candidate_id JOIN recruitment_jobs j ON j.tenant_id=i.tenant_id AND j.id=i.job_id WHERE i.tenant_id=:tenantId AND i.id=:id";
        return jdbc.query(sql,params.values,INTERVIEW).stream().findFirst().orElseThrow(()->notFound("Interview"));
    }

    public RecruitmentDtos.OverviewResponse overview(UUID tenantId) {
        return new RecruitmentDtos.OverviewResponse(statusCounts("recruitment_jobs",tenantId),
                statusCounts("recruitment_applications",tenantId),statusCounts("recruitment_interviews",tenantId),
                upcoming(tenantId));
    }

    public RecruitmentDtos.ApplicationDetailResponse applicationDetail(UUID tenantId,UUID id) {
        RecruitmentDtos.ApplicationResponse application=application(tenantId,id);
        Params params=new Params(tenantId,new Page(0,1));params.values.addValue("id",id);
        RecruitmentDtos.CandidateResponse candidate=jdbc.query("""
                SELECT c.* FROM recruitment_candidates c JOIN recruitment_applications a
                  ON a.tenant_id=c.tenant_id AND a.candidate_id=c.id
                WHERE a.tenant_id=:tenantId AND a.id=:id
                """,params.values,CANDIDATE).stream().findFirst().orElseThrow(()->notFound("Candidate"));
        Map<String,String> evidence=jdbc.query("SELECT screening_config_snapshot::text config,screening_answers::text answers FROM recruitment_applications WHERE tenant_id=:tenantId AND id=:id",
                params.values,rs->{if(!rs.next())return null;return Map.of("config",rs.getString(1),"answers",rs.getString(2));});
        if(evidence==null)throw notFound("Application");
        try {
            ObjectMapper mapper=objectMapper==null?new ObjectMapper():objectMapper;
            List<RecruitmentDtos.ScreeningQuestion> questions=screening==null
                    ?mapper.readValue(evidence.get("config"),new TypeReference<>(){}):screening.read(evidence.get("config"));
            List<PublicRecruitmentDtos.ScreeningAnswer> answers=mapper.readValue(evidence.get("answers"),new TypeReference<>(){});
            return new RecruitmentDtos.ApplicationDetailResponse(application,candidate,questions,answers);
        } catch(Exception exception){throw new IllegalStateException("Stored screening evidence is invalid",exception);}
    }

    public List<RecruitmentDtos.CallAttemptResponse> attempts(UUID tenantId,UUID interviewId) {
        Params params=new Params(tenantId,new Page(0,100));params.values.addValue("interviewId",interviewId);
        if(jdbc.queryForObject("SELECT count(*) FROM recruitment_interviews WHERE tenant_id=:tenantId AND id=:interviewId",params.values,Long.class)==0)
            throw notFound("Interview");
        return jdbc.query("""
                SELECT attempt_number,status,created_at,updated_at,answered_at,consented_at,terminal_at,failure_code
                FROM recruitment_interview_call_attempts WHERE tenant_id=:tenantId AND interview_id=:interviewId
                ORDER BY attempt_number
                """,params.values,(rs,row)->new RecruitmentDtos.CallAttemptResponse(rs.getInt(1),
                CallAttemptStatus.valueOf(rs.getString(2)),rs.getObject(3,LocalDateTime.class),
                rs.getObject(4,LocalDateTime.class),offsetInstant(rs,5),offsetInstant(rs,6),offsetInstant(rs,7),rs.getString(8)));
    }

    private Map<String,Long> statusCounts(String table,UUID tenantId){
        Params params=new Params(tenantId,new Page(0,1));Map<String,Long> result=new LinkedHashMap<>();
        jdbc.query("SELECT status,count(*) item_count FROM "+table+" WHERE tenant_id=:tenantId GROUP BY status ORDER BY status",
                params.values,rs->{while(rs.next())result.put(rs.getString(1),rs.getLong(2));return result;});return result;
    }

    private List<RecruitmentDtos.InterviewResponse> upcoming(UUID tenantId){
        Params params=new Params(tenantId,new Page(0,5));
        return jdbc.query("""
                SELECT i.*,j.title job_title,c.id candidate_id,c.full_name candidate_name
                FROM recruitment_interviews i
                JOIN recruitment_applications a ON a.tenant_id=i.tenant_id AND a.id=i.application_id
                JOIN recruitment_candidates c ON c.tenant_id=a.tenant_id AND c.id=a.candidate_id
                JOIN recruitment_jobs j ON j.tenant_id=i.tenant_id AND j.id=i.job_id
                WHERE i.tenant_id=:tenantId AND i.status='SCHEDULED' AND i.scheduled_start_at>=CURRENT_TIMESTAMP
                ORDER BY i.scheduled_start_at,i.id LIMIT 5
                """,params.values,INTERVIEW);
    }

    private <T> RecruitmentDtos.PageResult<T> result(List<T> items,String from,Params params){
        Long count=jdbc.queryForObject("SELECT count(*)"+from,params.values,Long.class);
        return new RecruitmentDtos.PageResult<>(items,count==null?0:count);
    }
    private static StringBuilder base(String alias){return new StringBuilder(" WHERE ").append(alias).append(".tenant_id = :tenantId");}

    private static void add(StringBuilder where,Params p,String condition,String name,Object value){if(value!=null){where.append(" AND ").append(condition);p.values.addValue(name,value instanceof Enum<?> e?e.name():value);}}
    private static void range(StringBuilder w,Params p,String col,LocalDateTime from,LocalDateTime to){if(from!=null){w.append(" AND ").append(col).append(" >= :from");p.values.addValue("from",from);}if(to!=null){w.append(" AND ").append(col).append(" < :to");p.values.addValue("to",to);}if(from!=null&&to!=null&&!from.isBefore(to))throw new BadRequestException("Range from must be before to");}
    private static void lowerUpper(StringBuilder w,Params p,String col,BigDecimal min,BigDecimal max){if(min!=null){w.append(" AND ").append(col).append(" >= :scoreMin");p.values.addValue("scoreMin",min);}if(max!=null){w.append(" AND ").append(col).append(" <= :scoreMax");p.values.addValue("scoreMax",max);}}
    private static void search(StringBuilder w,Params p,String q,String expression){if(q!=null&&!q.isBlank()){w.append(" AND ").append(expression).append(" ILIKE :search");p.values.addValue("search","%"+q.strip()+"%");}}
    private static void scoreRange(BigDecimal min,BigDecimal max){if(min!=null&&(min.signum()<0||min.compareTo(BigDecimal.valueOf(100))>0)||max!=null&&(max.signum()<0||max.compareTo(BigDecimal.valueOf(100))>0)||min!=null&&max!=null&&min.compareTo(max)>0)throw new BadRequestException("Score range must be between 0 and 100");}
    private static String order(String sort,String direction,Map<String,String> allowed,String defaultSort){String field=sort==null?defaultSort:sort;String col=allowed.get(field);if(col==null)throw new BadRequestException("Unknown sort field: "+field);String dir=direction==null?"DESC":direction.toUpperCase(Locale.ROOT);if(!dir.equals("ASC")&&!dir.equals("DESC"))throw new BadRequestException("Sort direction must be ASC or DESC");return " ORDER BY "+col+" "+dir+", "+col.substring(0,col.indexOf('.')+1)+"id "+dir;}
    private static String limit(){return " LIMIT :size OFFSET :offset";}
    private static Page page(int page,int size){if(page<0)throw new BadRequestException("page must be zero or greater");if(size<1||size>100)throw new BadRequestException("size must be between 1 and 100");return new Page(page,size);}
    private static ResourceNotFoundException notFound(String name){return new ResourceNotFoundException(name+" was not found");}

    private record Page(int page,int size){}
    private static final class Params {final MapSqlParameterSource values=new MapSqlParameterSource();Params(UUID tenantId,Page p){values.addValue("tenantId",tenantId);values.addValue("size",p.size);values.addValue("offset",p.page*p.size);}}

    private static String s(ResultSet r,String n)throws SQLException{return r.getString(n);} private static UUID u(ResultSet r,String n)throws SQLException{return r.getObject(n,UUID.class);} private static LocalDateTime t(ResultSet r,String n)throws SQLException{return r.getObject(n,LocalDateTime.class);} private static BigDecimal d(ResultSet r,String n)throws SQLException{return r.getBigDecimal(n);}
    private static java.time.Instant offsetInstant(ResultSet r,int index)throws SQLException{java.time.OffsetDateTime value=r.getObject(index,java.time.OffsetDateTime.class);return value==null?null:value.toInstant();}
    private static java.time.Instant offsetInstant(ResultSet r,String name)throws SQLException{java.time.OffsetDateTime value=r.getObject(name,java.time.OffsetDateTime.class);return value==null?null:value.toInstant();}
    private static final RowMapper<RecruitmentDtos.JobResponse> JOB=(r,n)->new RecruitmentDtos.JobResponse(u(r,"id"),u(r,"public_id"),s(r,"title"),s(r,"description"),s(r,"department"),s(r,"location"),enumOrNull(EmploymentType.class,s(r,"employment_type")),enumOrNull(WorkMode.class,s(r,"work_mode")),enumOrNull(ExperienceLevel.class,s(r,"experience_level")),s(r,"language"),JobStatus.valueOf(s(r,"status")),CvPolicy.valueOf(s(r,"cv_policy")),enumOrNull(AutomationMode.class,s(r,"automation_mode_override")),enumOrNull(CvAiMode.class,s(r,"cv_ai_mode_override")),enumOrNull(AutomationMode.class,s(r,"effective_automation_mode")),enumOrNull(CvAiMode.class,s(r,"effective_cv_ai_mode")),r.getBoolean("recording_enabled"),r.getInt("recording_retention_days"),u(r,"template_revision_id"),t(r,"closing_at"),t(r,"published_at"),t(r,"paused_at"),t(r,"closed_at"),t(r,"archived_at"),u(r,"active_job_reservation_id"),s(r,"frozen_company_name"),s(r,"frozen_company_slug"),r.getLong("version"),t(r,"created_at"),t(r,"updated_at"));
    private static final RowMapper<RecruitmentDtos.TemplateResponse> TEMPLATE=(r,n)->new RecruitmentDtos.TemplateResponse(u(r,"id"),s(r,"name"),s(r,"description"),s(r,"locale"),r.getBoolean("archived"),t(r,"archived_at"),r.getInt("latest_revision_number"),r.getLong("version"),t(r,"created_at"),t(r,"updated_at"));
    private static final RowMapper<RecruitmentDtos.CandidateResponse> CANDIDATE=(r,n)->new RecruitmentDtos.CandidateResponse(u(r,"id"),s(r,"full_name"),s(r,"email"),s(r,"phone"),s(r,"notes"),r.getLong("version"),t(r,"created_at"),t(r,"updated_at"));
    private static final RowMapper<RecruitmentDtos.ApplicationResponse> APPLICATION=(r,n)->new RecruitmentDtos.ApplicationResponse(u(r,"id"),u(r,"job_id"),s(r,"job_title"),u(r,"candidate_id"),s(r,"candidate_name"),s(r,"candidate_email"),ApplicationStatus.valueOf(s(r,"status")),t(r,"submitted_at"),t(r,"verified_at"),t(r,"withdrawn_at"),s(r,"locale"),r.getBoolean("cv_present"),CvAnalysisStatus.valueOf(s(r,"cv_analysis_status")),u(r,"template_revision_id"),s(r,"template_snapshot_sha256"),s(r,"template_snapshot_version"),d(r,"overall_score"),s(r,"english_band"),enumOrNull(InterviewStatus.class,s(r,"interview_status")),r.getLong("version"),t(r,"created_at"),t(r,"updated_at"));
    private static final RowMapper<RecruitmentDtos.InterviewResponse> INTERVIEW=(r,n)->new RecruitmentDtos.InterviewResponse(u(r,"id"),u(r,"application_id"),u(r,"job_id"),s(r,"job_title"),u(r,"candidate_id"),s(r,"candidate_name"),InterviewStatus.valueOf(s(r,"status")),u(r,"template_revision_id"),s(r,"template_snapshot_sha256"),s(r,"template_snapshot_version"),t(r,"scheduled_at"),offsetInstant(r,"scheduled_start_at"),offsetInstant(r,"scheduled_end_at"),s(r,"scheduling_timezone"),r.getInt("reschedule_count"),t(r,"started_at"),t(r,"completed_at"),d(r,"overall_score"),s(r,"english_band"),r.getLong("version"),t(r,"created_at"),t(r,"updated_at"));
    private static <E extends Enum<E>> E enumOrNull(Class<E> type,String value){return value==null?null:Enum.valueOf(type,value);}
}
