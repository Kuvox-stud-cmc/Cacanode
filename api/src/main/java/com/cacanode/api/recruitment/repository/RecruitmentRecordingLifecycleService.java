package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentInterview;
import com.cacanode.api.recruitment.model.RecruitmentInterviewCallAttempt;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="recording-enabled",havingValue="true")
public class RecruitmentRecordingLifecycleService {
    private final JdbcTemplate jdbc;
    private final RecruitmentCallingProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Transactional
    public void enqueueStart(RecruitmentInterviewCallAttempt attempt,RecruitmentInterview interview) {
        if(!interview.isRecordingEnabled()||attempt.getConsentedAt()==null)return;
        Integer existing=jdbc.query("SELECT count(*) FROM recruitment_interview_recordings WHERE tenant_id=? AND call_attempt_id=?",
                rs->{rs.next();return rs.getInt(1);},attempt.getTenantId(),attempt.getId());if(existing!=null&&existing>0)return;
        UUID recordingId=UUID.randomUUID();Instant retainedUntil=attempt.getConsentedAt().plus(interview.getRecordingRetentionDays(), ChronoUnit.DAYS);
        jdbc.update("INSERT INTO recruitment_interview_recordings(id,tenant_id,session_id,call_attempt_id,state,retained_until) VALUES (?,?,?,?,?,?)",
                recordingId,attempt.getTenantId(),attempt.getSessionId(),attempt.getId(),"START_PENDING",retainedUntil);
        jdbc.update("INSERT INTO recruitment_recording_operations(tenant_id,recording_id,operation_kind,operation_key) VALUES (?,?,?,?)",
                attempt.getTenantId(),recordingId,"START","recording:"+recordingId+":start");
    }

    @Transactional
    public void acceptStatus(RecruitmentInterviewCallAttempt attempt,MultiValueMap<String,String> form) {
        String account=form.getFirst("AccountSid"),call=form.getFirst("CallSid"),sid=form.getFirst("RecordingSid"),status=form.getFirst("RecordingStatus");
        if(!properties.twilioAccountSid().equals(account)||!Objects.equals(attempt.getTwilioCallSid(),call)
                ||sid==null||!sid.matches("^RE[0-9a-fA-F]{32}$"))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"RECORDING_CALLBACK_BINDING_MISMATCH");
        Map<String,List<String>> canonical=new TreeMap<>();form.forEach((key,value)->canonical.put(key,List.copyOf(value)));
        String hash=sha256(write(canonical));Map<String,Object> row=jdbc.query("SELECT id,callback_payload_sha256 FROM recruitment_interview_recordings WHERE tenant_id=? AND call_attempt_id=? FOR UPDATE",
                rs->{if(!rs.next())return null;Map<String,Object> value=new HashMap<>();value.put("id",rs.getObject(1,UUID.class));value.put("hash",rs.getString(2));return value;},attempt.getTenantId(),attempt.getId());
        if(row==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"RECORDING_NOT_REQUESTED");
        String previous=(String)row.get("hash");if(previous!=null){if(previous.equals(hash))return;throw new ResponseStatusException(HttpStatus.CONFLICT,"RECORDING_CALLBACK_CONFLICT");}
        UUID recordingId=(UUID)row.get("id");if(!"completed".equalsIgnoreCase(status)){
            jdbc.update("UPDATE recruitment_interview_recordings SET state='FAILED',provider_account_sid=?,provider_recording_sid=?,callback_payload_sha256=?,failure_code=?,updated_at=NOW() WHERE id=?",
                    account,sid,hash,"TWILIO_RECORDING_"+(status==null?"UNKNOWN":status.toUpperCase(Locale.ROOT)),recordingId);return;}
        Integer days=jdbc.query("SELECT recording_retention_days FROM recruitment_interviews WHERE tenant_id=? AND id=?",rs->rs.next()?rs.getInt(1):null,attempt.getTenantId(),attempt.getSessionId());
        Instant completed=clock.instant();Instant retained=completed.plus(days==null?0:days,ChronoUnit.DAYS);
        int duration=parseDuration(form.getFirst("RecordingDuration"));
        jdbc.update("UPDATE recruitment_interview_recordings SET state='COPY_PENDING',provider_account_sid=?,provider_recording_sid=?,callback_payload_sha256=?,recording_completed_at=?,retained_until=?,recording_duration_seconds=?,updated_at=NOW() WHERE id=?",
                account,sid,hash,completed,retained,duration,recordingId);
        jdbc.update("INSERT INTO recruitment_recording_operations(tenant_id,recording_id,operation_kind,operation_key) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                attempt.getTenantId(),recordingId,"COPY","recording:"+recordingId+":copy");
    }

    private String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static int parseDuration(String value){try{return Math.max(0,Math.min(14400,Integer.parseInt(value)));}catch(Exception ignored){return 0;}}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
