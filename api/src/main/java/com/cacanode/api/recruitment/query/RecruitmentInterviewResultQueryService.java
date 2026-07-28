package com.cacanode.api.recruitment.query;

import com.cacanode.api.recruitment.dto.InterviewResultDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class RecruitmentInterviewResultQueryService {
    private final JdbcTemplate jdbc;

    public InterviewResultDtos.Transcript transcript(UUID tenantId,UUID interviewId,int page,int size) {
        bounds(page,size);requireInterview(tenantId,interviewId);
        Map<String,Object> result=jdbc.query("SELECT delivery_status,expected_turn_count,persisted_turn_count FROM recruitment_interview_results WHERE tenant_id=? AND session_id=?",
                rs->{if(!rs.next())return null;Map<String,Object> row=new HashMap<>();row.put("status",rs.getString(1));row.put("expected",rs.getInt(2));row.put("persisted",rs.getInt(3));return row;},tenantId,interviewId);
        List<InterviewResultDtos.Turn> turns=jdbc.query("""
                SELECT turn_id,sequence_number,speaker,turn_kind,section_id,question_id,language_tag,
                started_at_epoch_ms,ended_at_epoch_ms,transcript,interrupted
                FROM recruitment_interview_transcript_turns WHERE tenant_id=? AND session_id=?
                ORDER BY sequence_number LIMIT ? OFFSET ?
                """,(rs,row)->new InterviewResultDtos.Turn(rs.getObject(1,UUID.class),rs.getInt(2),rs.getString(3),
                rs.getString(4),rs.getObject(5,UUID.class),rs.getObject(6,UUID.class),rs.getString(7),rs.getLong(8),
                rs.getLong(9),rs.getString(10),rs.getBoolean(11)),tenantId,interviewId,size,page*size);
        return new InterviewResultDtos.Transcript(interviewId,result==null?"PENDING_RESULT":(String)result.get("status"),
                result==null?0:(Integer)result.get("expected"),result==null?turns.size():(Integer)result.get("persisted"),page,size,turns);
    }

    public InterviewResultDtos.Result result(UUID tenantId,UUID interviewId) {
        requireInterview(tenantId,interviewId);ResultRow row=jdbc.query("""
                SELECT terminal_kind,delivery_status,completion_reason,failure_code,retryable,failure_detail,partial,
                expected_turn_count,persisted_turn_count,connected_seconds,score_policy_version,overall_score,
                english_comprehension,english_fluency,english_vocabulary,english_grammar,english_pronunciation,
                english_band,advisory_only,occurred_at FROM recruitment_interview_results
                WHERE tenant_id=? AND session_id=?
                """,rs->rs.next()?new ResultRow(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                (Boolean)rs.getObject(5),rs.getString(6),rs.getBoolean(7),rs.getInt(8),rs.getInt(9),rs.getInt(10),
                rs.getString(11),rs.getBigDecimal(12),dimensions(rs.getBigDecimal(13),rs.getBigDecimal(14),
                rs.getBigDecimal(15),rs.getBigDecimal(16),rs.getBigDecimal(17)),rs.getString(18),rs.getBoolean(19),
                rs.getObject(20,OffsetDateTime.class)):null,tenantId,interviewId);
        if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"INTERVIEW_RESULT_NOT_AVAILABLE");
        List<InterviewResultDtos.SectionResult> sections=new ArrayList<>();
        ResultSetExtractor<Void> sectionExtractor=rs->{
            while(rs.next()){
                UUID sectionId=rs.getObject(1,UUID.class);
                List<InterviewResultDtos.QuestionResult> questions=new ArrayList<>();
                ResultSetExtractor<Void> questionExtractor=qrs->{
                    while(qrs.next()){
                        UUID questionId=qrs.getObject(1,UUID.class);
                        List<InterviewResultDtos.Evaluation> evaluations=jdbc.query("""
                                SELECT candidate_turn_id,accepted,rubric_score,english_comprehension,english_fluency,
                                english_vocabulary,english_grammar,english_pronunciation FROM recruitment_interview_score_evaluations
                                WHERE tenant_id=? AND session_id=? AND question_id=? ORDER BY position
                                """,(ers,index)->new InterviewResultDtos.Evaluation(ers.getObject(1,UUID.class),ers.getBoolean(2),
                                ers.getBigDecimal(3),dimensions(ers.getBigDecimal(4),ers.getBigDecimal(5),ers.getBigDecimal(6),
                                ers.getBigDecimal(7),ers.getBigDecimal(8))),tenantId,interviewId,questionId);
                        questions.add(new InterviewResultDtos.QuestionResult(questionId,qrs.getString(2),qrs.getString(3),
                                qrs.getBigDecimal(4),evaluations.stream().map(InterviewResultDtos.Evaluation::candidateTurnId).toList(),evaluations));
                    }
                    return null;
                };
                jdbc.query("SELECT question_id,section_kind,question_status,question_score FROM recruitment_interview_question_results WHERE tenant_id=? AND session_id=? AND section_id=? ORDER BY position",
                        questionExtractor,tenantId,interviewId,sectionId);
                sections.add(new InterviewResultDtos.SectionResult(sectionId,rs.getString(2),rs.getString(3),questions));
            }
            return null;
        };
        jdbc.query("SELECT section_id,section_kind,section_status FROM recruitment_interview_section_results WHERE tenant_id=? AND session_id=? ORDER BY position",
                sectionExtractor,tenantId,interviewId);
        return new InterviewResultDtos.Result(interviewId,row.terminalKind,row.deliveryStatus,row.completionReason,
                row.failureCode,row.retryable,row.failureDetail,row.partial,row.expectedTurns,row.persistedTurns,row.connectedSeconds,
                row.policy,row.overall,row.english,row.band,row.advisoryOnly,InterviewResultDtos.ENGLISH_WARNING,row.occurredAt,sections);
    }

    public List<InterviewResultDtos.Recording> recordings(UUID tenantId,UUID interviewId) {
        requireInterview(tenantId,interviewId);return jdbc.query("""
                SELECT id,state,content_type,size_bytes,retained_until,ready_at,deleted_at
                FROM recruitment_interview_recordings WHERE tenant_id=? AND session_id=? ORDER BY created_at,id
                """,(rs,row)->new InterviewResultDtos.Recording(rs.getObject(1,UUID.class),rs.getString(2),
                rs.getString(3),(Long)rs.getObject(4),rs.getObject(5,OffsetDateTime.class),
                rs.getObject(6,OffsetDateTime.class),rs.getObject(7,OffsetDateTime.class)),tenantId,interviewId);
    }

    public RecordingAccess recording(UUID tenantId,UUID interviewId,UUID recordingId) {
        requireInterview(tenantId,interviewId);RecordingAccess value=jdbc.query("""
                SELECT storage_key,content_type,size_bytes FROM recruitment_interview_recordings
                WHERE tenant_id=? AND session_id=? AND id=? AND state='READY' AND deleted_at IS NULL
                """,rs->rs.next()?new RecordingAccess(rs.getString(1),rs.getString(2),rs.getLong(3)):null,
                tenantId,interviewId,recordingId);if(value==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"RECORDING_NOT_AVAILABLE");return value;
    }

    private void requireInterview(UUID tenantId,UUID interviewId){Integer count=jdbc.query("SELECT count(*) FROM recruitment_interviews WHERE tenant_id=? AND id=?",rs->{rs.next();return rs.getInt(1);},tenantId,interviewId);if(count==null||count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"INTERVIEW_NOT_FOUND");}
    private static void bounds(int page,int size){if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_PAGINATION");}
    private static InterviewResultDtos.EnglishDimensions dimensions(BigDecimal a,BigDecimal b,BigDecimal c,BigDecimal d,BigDecimal e){return a==null&&b==null&&c==null&&d==null&&e==null?null:new InterviewResultDtos.EnglishDimensions(a,b,c,d,e);}
    private record ResultRow(String terminalKind,String deliveryStatus,String completionReason,String failureCode,
            Boolean retryable,String failureDetail,boolean partial,int expectedTurns,int persistedTurns,int connectedSeconds,
            String policy,BigDecimal overall,InterviewResultDtos.EnglishDimensions english,String band,
            boolean advisoryOnly,OffsetDateTime occurredAt){}
    public record RecordingAccess(String storageKey,String contentType,long sizeBytes){}
}
