package com.cacanode.api.recruitment.repository;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.common.event.durable.DurableEventPublisher;
import com.cacanode.api.common.storage.DocumentStorage;
import com.cacanode.api.recruitment.api.InterviewEventIdentity;
import com.cacanode.api.recruitment.api.event.RecordingReadyEvent;
import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.cacanode.api.recruitment.service.RecordingTransport;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

import static com.cacanode.api.recruitment.repository.RecruitmentJdbcTypes.timestamptz;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="recording-enabled",havingValue="true")
public class RecruitmentRecordingOperationWorker {
    private static final long MAX_RECORDING_BYTES=1024L*1024*1024;
    private static final String OPERATION_SELECT="""
            SELECT o.id,o.tenant_id,o.recording_id,o.operation_kind,o.attempts,r.session_id,
            r.call_attempt_id,r.provider_recording_sid,r.storage_key,r.size_bytes,r.sha256,
            r.storage_reservation_id,r.retained_until,ca.twilio_call_sid,ca.consented_at
            FROM recruitment_recording_operations o JOIN recruitment_interview_recordings r
            ON r.tenant_id=o.tenant_id AND r.id=o.recording_id
            JOIN recruitment_interview_call_attempts ca
            ON ca.tenant_id=r.tenant_id AND ca.id=r.call_attempt_id
            """;
    private final JdbcTemplate jdbc;private final RecordingTransport transport;private final DocumentStorage storage;
    private final HiringQuotaApi quota;private final RecruitmentCallingProperties properties;
    private final DurableEventPublisher events;private final Clock clock;

    @Scheduled(fixedDelayString="${app.recruitment.recording.operation-recovery-delay-ms:30000}")
    @Transactional
    public void processOne() {
        Operation operation=queryOperation("""
                WHERE o.status='PENDING' AND o.next_attempt_at<=NOW()
                  AND o.notification_published_at IS NOT NULL
                ORDER BY o.next_attempt_at,o.id
                FOR UPDATE OF o SKIP LOCKED LIMIT 1
                """);
        execute(operation);
    }

    @Transactional
    public void process(UUID operationId) {
        Operation operation=queryOperation("""
                WHERE o.id=? AND o.status='PENDING' AND o.next_attempt_at<=NOW()
                FOR UPDATE OF o
                """,operationId);
        execute(operation);
    }

    private void execute(Operation operation) {
        if(operation==null)return;
        jdbc.update("UPDATE recruitment_recording_operations SET status='PROCESSING',locked_at=NOW(),updated_at=NOW() WHERE id=?",operation.id);
        try {switch(operation.kind){case "START"->start(operation);case "STOP"->stop(operation);case "COPY"->copy(operation);case "DELETE_PROVIDER"->deleteProvider(operation);case "DELETE_STORAGE","VERIFY_DELETION"->deleteStorage(operation);default->throw new IllegalStateException("Unknown recording operation");}
            jdbc.update("UPDATE recruitment_recording_operations SET status='COMPLETED',completed_at=NOW(),locked_at=NULL,updated_at=NOW() WHERE id=?",operation.id);
        } catch(RuntimeException exception){retry(operation,exception);}
    }

    @Scheduled(fixedDelayString="${app.recruitment.recording.retention-delay-ms:60000}")
    @Transactional
    public void enqueueRetentionDeletes() {
        jdbc.update("""
                INSERT INTO recruitment_recording_operations(tenant_id,recording_id,operation_kind,operation_key)
                SELECT r.tenant_id,r.id,'DELETE_STORAGE','recording:'||r.id||':delete'
                FROM recruitment_interview_recordings r JOIN recruitment_interviews i
                ON i.tenant_id=r.tenant_id AND i.id=r.session_id JOIN recruitment_applications a
                ON a.tenant_id=i.tenant_id AND a.id=i.application_id
                WHERE r.state<>'DELETED' AND (r.retained_until<=NOW() OR a.status='WITHDRAWN')
                ON CONFLICT DO NOTHING
                """);
    }

    private void start(Operation operation) {
        if(clock.instant().isAfter(operation.consentedAt.plusSeconds(60))) {jdbc.update("UPDATE recruitment_interview_recordings SET state='FAILED',failure_code='RECORDING_START_WINDOW_EXPIRED',updated_at=NOW() WHERE id=?",operation.recordingId);return;}
        RecordingTransport.Recording recording=transport.findForCall(operation.callSid).orElseGet(()->transport.startDualChannelMp3(operation.callSid,
                properties.callbackBaseUrl().replaceAll("/+$","")+"/api/v1/public/twilio/interviews/recording-status?attempt="+operation.callAttemptId));
        jdbc.update("UPDATE recruitment_interview_recordings SET state='RECORDING',provider_account_sid=?,provider_recording_sid=?,failure_code=NULL,updated_at=NOW() WHERE id=?",
                properties.twilioAccountSid(),recording.recordingSid(),operation.recordingId);
    }

    private void stop(Operation operation) {
        if(operation.providerSid!=null)transport.stop(operation.providerSid);
        jdbc.update("UPDATE recruitment_interview_recordings SET failure_code='RECORDING_STOPPED_BY_KILL_SWITCH',updated_at=NOW() WHERE id=?",operation.recordingId);
    }

    private void copy(Operation operation) {
        if(operation.providerSid==null)throw new IllegalStateException("Recording callback has no provider SID");Path temporary=null;UUID reservation=null;
        try {temporary=Files.createTempFile("cacanode-recording-",".mp3");long size;try(var output=Files.newOutputStream(temporary,StandardOpenOption.TRUNCATE_EXISTING)){size=transport.downloadMp3(operation.providerSid,output,MAX_RECORDING_BYTES);}
            String hash=sha256(temporary);reservation=quota.reserveStorage(operation.tenantId,operation.recordingId,size).reservationId();
            String key="recruitment/"+operation.tenantId+"/interviews/"+operation.sessionId+"/recordings/"+operation.recordingId+".mp3";
            try(InputStream input=Files.newInputStream(temporary)){storage.store(key,input,size,"audio/mpeg");}
            verify(key,size,hash);quota.commitStorage(operation.tenantId,reservation,size);
            jdbc.update("UPDATE recruitment_interview_recordings SET state='DELETE_PROVIDER_PENDING',storage_key=?,content_type='audio/mpeg',size_bytes=?,sha256=?,storage_reservation_id=?,failure_code=NULL,updated_at=NOW() WHERE id=?",
                    key,size,hash,reservation,operation.recordingId);
            jdbc.update("INSERT INTO recruitment_recording_operations(tenant_id,recording_id,operation_kind,operation_key) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                    operation.tenantId,operation.recordingId,"DELETE_PROVIDER","recording:"+operation.recordingId+":delete-provider");
        } catch(RuntimeException exception){if(reservation!=null)try{quota.releaseStorage(operation.tenantId,reservation);}catch(RuntimeException ignored){}throw exception;}
        catch(Exception exception){throw new IllegalStateException("RECORDING_COPY_FAILED",exception);}finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(Exception ignored){}}
    }

    private void deleteProvider(Operation operation) {
        if(operation.providerSid!=null&&transport.exists(operation.providerSid))transport.delete(operation.providerSid);
        if(operation.providerSid!=null&&transport.exists(operation.providerSid))throw new RecordingTransport.UncertainFailure("TWILIO_RECORDING_DELETE_UNCONFIRMED",null);
        Instant now=clock.instant();jdbc.update("UPDATE recruitment_interview_recordings SET state='READY',provider_deleted_at=?,ready_at=?,failure_code=NULL,updated_at=NOW() WHERE id=?",timestamptz(now),timestamptz(now),operation.recordingId);
        RecordingReadyEvent event=new RecordingReadyEvent("1.0",InterviewEventIdentity.eventId("recruitment.recording.ready",operation.sessionId,"recording:"+operation.recordingId),
                "recruitment.recording.ready",now,operation.tenantId,operation.sessionId,operation.sessionId,operation.callAttemptId,
                operation.storageKey,"audio/mpeg",operation.sizeBytes==null?0:operation.sizeBytes,operation.sha256,operation.retainedUntil);
        events.publish("recruitment.recording.ready",1,event);
    }

    private void deleteStorage(Operation operation) {
        if(operation.storageKey!=null&&storage.exists(operation.storageKey))storage.delete(operation.storageKey);
        if(operation.storageKey!=null&&storage.exists(operation.storageKey))throw new IllegalStateException("RECORDING_STORAGE_DELETE_UNCONFIRMED");
        if(operation.providerSid!=null&&transport.exists(operation.providerSid))transport.delete(operation.providerSid);
        if(operation.providerSid!=null&&transport.exists(operation.providerSid))throw new IllegalStateException("RECORDING_PROVIDER_DELETE_UNCONFIRMED");
        if(operation.storageReservationId!=null)quota.releaseStorage(operation.tenantId,operation.storageReservationId);
        Instant deletedAt=clock.instant();
        jdbc.update("UPDATE recruitment_interview_recordings SET state='DELETED',deleted_at=?,provider_deleted_at=COALESCE(provider_deleted_at,?),failure_code=NULL,updated_at=NOW() WHERE id=?",
                timestamptz(deletedAt),timestamptz(deletedAt),operation.recordingId);
    }

    private void verify(String key,long size,String expected) throws Exception {var metadata=storage.metadata(key);if(metadata.contentLength()!=size)throw new IllegalStateException("RECORDING_SIZE_MISMATCH");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");for(long start=0;start<size;start+=1024*1024){long end=Math.min(size-1,start+1024*1024-1);digest.update(storage.loadRange(key,start,end).content());}
        if(!HexFormat.of().formatHex(digest.digest()).equals(expected))throw new IllegalStateException("RECORDING_HASH_MISMATCH");}
    private static String sha256(Path file) throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=new DigestInputStream(Files.newInputStream(file),digest)){input.transferTo(OutputStream.nullOutputStream());}return HexFormat.of().formatHex(digest.digest());}
    private Operation queryOperation(String suffix,Object... arguments){return jdbc.query(OPERATION_SELECT+suffix,rs->rs.next()?new Operation(
            rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getString(4),
            rs.getInt(5),rs.getObject(6,UUID.class),rs.getObject(7,UUID.class),rs.getString(8),rs.getString(9),
            (Long)rs.getObject(10),rs.getString(11),rs.getObject(12,UUID.class),instant(rs.getObject(13,OffsetDateTime.class)),
            rs.getString(14),instant(rs.getObject(15,OffsetDateTime.class))):null,arguments);}
    private static Instant instant(OffsetDateTime value){return value==null?null:value.toInstant();}
    private void retry(Operation operation,RuntimeException exception){int attempts=operation.attempts+1;if(attempts>=10){jdbc.update("UPDATE recruitment_recording_operations SET status='DEAD',attempts=?,last_error_code=?,locked_at=NULL,updated_at=NOW() WHERE id=?",attempts,code(exception),operation.id);return;}
        long delay=Math.min(3600,5L*(1L<<Math.min(attempts,10)));Instant nextAttempt=clock.instant().plusSeconds(delay);jdbc.update("""
                UPDATE recruitment_recording_operations
                SET status='PENDING',attempts=?,last_error_code=?,next_attempt_at=?,locked_at=NULL,
                    notification_published_at=NULL,notification_next_attempt_at=?,
                    notification_last_error_code=NULL,updated_at=NOW()
                WHERE id=?
                """,attempts,code(exception),timestamptz(nextAttempt),timestamptz(nextAttempt),operation.id);}
    private static String code(Throwable value){String text=value.getMessage();return text==null?value.getClass().getSimpleName():text.substring(0,Math.min(100,text.length()));}
    private record Operation(UUID id,UUID tenantId,UUID recordingId,String kind,int attempts,UUID sessionId,
            UUID callAttemptId,String providerSid,String storageKey,Long sizeBytes,String sha256,UUID storageReservationId,
            Instant retainedUntil,String callSid,Instant consentedAt){}
}
