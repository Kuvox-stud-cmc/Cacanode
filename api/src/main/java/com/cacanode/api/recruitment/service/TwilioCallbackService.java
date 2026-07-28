package com.cacanode.api.recruitment.service;

import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.security.RequestValidator;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="calling-enabled",havingValue="true")
public class TwilioCallbackService {
    private static final long STALE_PRE_ANSWER_SECONDS=120;
    private static final String FALLBACK_DISCLOSURE="This is an automated AI interview. "
            +"Your responses will be processed for recruitment and may be recorded when recording is enabled.";
    private final RecruitmentCallingProperties properties;
    private final RecruitmentInterviewCallAttemptRepository attempts;
    private final RecruitmentTwilioCallbackInboxRepository inbox;
    private final RecruitmentInterviewRepository interviews;
    private final HiringQuotaApi quota;
    private final InterviewInferenceApi inference;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final RecruitmentInterviewTransportReconciliationRepository transportReconciliation;
    private final RecruitmentProjectionEventPublisher projectionEvents;
    @Autowired(required=false) private RecruitmentRecordingLifecycleService recordings;

    public void validate(HttpServletRequest request,MultiValueMap<String,String> form) {
        String signature=request.getHeader("X-Twilio-Signature");
        Map<String,String> parameters=new TreeMap<>();
        form.forEach((key,values)->parameters.put(key,values.isEmpty()?"":values.get(values.size()-1)));
        String base=properties.callbackBaseUrl().replaceAll("/+$","");
        String url=base+request.getRequestURI()+(request.getQueryString()==null?"":"?"+request.getQueryString());
        if(signature==null||!new RequestValidator(properties.twilioAuthToken()).validate(url,parameters,signature)) {
            metrics.counter("recruitment.twilio.callback","result","invalid_signature").increment();
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"INVALID_TWILIO_SIGNATURE");
        }
        metrics.counter("recruitment.twilio.callback","result","signature_valid").increment();
    }

    @Transactional
    public RecruitmentInterviewCallAttempt bind(UUID attemptId,MultiValueMap<String,String> form,
            TwilioCallbackKind kind,String semanticKey) {
        RecruitmentInterviewCallAttempt attempt=attempts.findForUpdate(attemptId)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        String callSid=form.getFirst("CallSid");
        if(callSid==null||!callSid.matches("^CA[0-9a-fA-F]{32}$"))rejectBinding();
        if(attempt.getTwilioCallSid()==null&&attempt.isCreateOutcomeUncertain()) {
            attempt.setTwilioCallSid(callSid);attempt.setCreateOutcomeUncertain(false);
            attempt.setCreateUncertainUntil(null);attempt.setFailureCode(null);
        } else if(!callSid.equals(attempt.getTwilioCallSid()))rejectBinding();
        if(terminal(attempt.getStatus())) {
            record(attempt,kind,null,semanticKey,form,TwilioCallbackResult.IGNORED_TERMINAL);
            return attempt;
        }
        if(kind==TwilioCallbackKind.VOICE) {
            attempt.setStatus(CallAttemptStatus.CONSENT_PENDING);
            if(attempt.getAnsweredAt()==null)attempt.setAnsweredAt(clock.instant());
            RecruitmentInterview interview=interviews.findForUpdate(attempt.getTenantId(),attempt.getInterviewId()).orElseThrow();
            interview.setStatus(InterviewStatus.CONSENT_PENDING);interviews.save(interview);
        }
        record(attempt,kind,null,semanticKey,form,TwilioCallbackResult.APPLIED);
        attempts.save(attempt);return attempt;
    }

    @Transactional
    public void status(UUID attemptId,MultiValueMap<String,String> form) {
        RecruitmentInterviewCallAttempt attempt=attempts.findForUpdate(attemptId)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        String callSid=form.getFirst("CallSid");
        if(callSid==null||(!attempt.isCreateOutcomeUncertain()&&attempt.getTwilioCallSid()!=null
                &&!attempt.getTwilioCallSid().equals(callSid)))rejectBinding();
        if(attempt.getTwilioCallSid()==null&&attempt.isCreateOutcomeUncertain()&&callSid!=null) {
            attempt.setTwilioCallSid(callSid);attempt.setCreateOutcomeUncertain(false);
            attempt.setCreateUncertainUntil(null);attempt.setFailureCode(null);
        }
        String rawStatus=safe(form.getFirst("CallStatus")).toLowerCase(Locale.ROOT);
        if(terminalTwilioStatus(rawStatus))settleTerminalDuration(attempt,form.getFirst("CallDuration"));
        long sequence=parseSequence(form.getFirst("SequenceNumber"));
        String semantic="sequence:"+sequence;
        String digest=payloadHash(form);
        var existing=inbox.findByCallAttemptIdAndCallbackKindAndSemanticKey(attemptId,TwilioCallbackKind.STATUS,semantic);
        if(existing.isPresent()) {
            if(existing.get().getPayloadSha256().equals(digest)) {metric("duplicate");return;}
            existing.get().setProcessingResult(TwilioCallbackResult.REJECTED_CONFLICT);inbox.save(existing.get());
            metric("sequence_conflict");throw new ResponseStatusException(HttpStatus.CONFLICT,"TWILIO_SEQUENCE_CONFLICT");
        }
        if(sequence<attempt.getCallbackSequence()) {
            record(attempt,TwilioCallbackKind.STATUS,sequence,semantic,form,TwilioCallbackResult.IGNORED_OLDER);
            metric("older_ignored");return;
        }
        if(sequence==attempt.getCallbackSequence()) {
            record(attempt,TwilioCallbackKind.STATUS,sequence,semantic,form,TwilioCallbackResult.REJECTED_CONFLICT);
            metric("sequence_conflict");throw new ResponseStatusException(HttpStatus.CONFLICT,"TWILIO_SEQUENCE_CONFLICT");
        }
        if(terminal(attempt.getStatus())) {
            record(attempt,TwilioCallbackKind.STATUS,sequence,semantic,form,TwilioCallbackResult.IGNORED_TERMINAL);
            metric("terminal_ignored");return;
        }
        attempt.setCallbackSequence(sequence);
        applyStatus(attempt,rawStatus);
        record(attempt,TwilioCallbackKind.STATUS,sequence,semantic,form,TwilioCallbackResult.APPLIED);
        attempts.save(attempt);metric("applied");
    }

    @Transactional
    public void consent(RecruitmentInterviewCallAttempt attempt,boolean accepted,String failureCode) {
        if(terminal(attempt.getStatus()))return;
        RecruitmentInterview interview=interviews.findForUpdate(attempt.getTenantId(),attempt.getInterviewId()).orElseThrow();
        if(accepted) {
            attempt.setStatus(CallAttemptStatus.IN_PROGRESS);attempt.setConsentedAt(clock.instant());
            interview.setStatus(InterviewStatus.IN_PROGRESS);interview.setStartedAt(LocalDateTime.now(clock));
        } else {
            boolean noConsentReceived="CONSENT_NOT_RECEIVED".equals(failureCode);
            Instant now=clock.instant();attempt.setStatus(noConsentReceived
                    ?CallAttemptStatus.NO_ANSWER:CallAttemptStatus.DECLINED);attempt.setFailureCode(failureCode);
            attempt.setTerminalAt(now);interview.setStatus(noConsentReceived
                    ?InterviewStatus.NO_ANSWER:InterviewStatus.DECLINED);
            interview.setActiveCallAttemptId(null);interview.setCompletedAt(LocalDateTime.now(clock));
        }
        attempts.save(attempt);interviews.save(interview);
        if(!accepted)cancelPreparedRuntime(attempt,failureCode);
        if(projectionEvents!=null)projectionEvents.interview(interview,accepted?"interview.started":
                interview.getStatus()==InterviewStatus.NO_ANSWER?"interview.no_answer":"interview.declined");
        if(accepted&&recordings!=null)recordings.enqueueStart(attempt,interview);
    }

    @Transactional
    public void fallback(UUID attemptId,MultiValueMap<String,String> form) {
        RecruitmentInterviewCallAttempt attempt=attempts.findForUpdate(attemptId).orElseThrow();
        if(terminal(attempt.getStatus())) {record(attempt,TwilioCallbackKind.FALLBACK,null,"fallback",form,
                TwilioCallbackResult.IGNORED_TERMINAL);return;}
        terminal(attempt,CallAttemptStatus.FAILED,InterviewStatus.FAILED,"TWILIO_FALLBACK",attempt.getAnsweredAt()==null);
        record(attempt,TwilioCallbackKind.FALLBACK,null,"fallback",form,TwilioCallbackResult.APPLIED);
    }

    @Transactional
    public void streamStatus(UUID attemptId,MultiValueMap<String,String> form) {
        RecruitmentInterviewCallAttempt attempt=attempts.findForUpdate(attemptId).orElseThrow();
        String streamSid=form.getFirst("StreamSid");
        String status=form.getFirst("StreamEvent");
        String key="stream:"+safe(streamSid)+":"+safe(status);
        record(attempt,TwilioCallbackKind.STREAM_STATUS,null,key,form,terminal(attempt.getStatus())
                ?TwilioCallbackResult.IGNORED_TERMINAL:TwilioCallbackResult.APPLIED);
    }

    @Transactional
    public void recordingStatus(RecruitmentInterviewCallAttempt attempt,MultiValueMap<String,String> form) {
        if(recordings==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"RECORDING_DISABLED");
        recordings.acceptStatus(attempt,form);
    }

    public String disclosure(RecruitmentInterviewCallAttempt attempt) {
        JsonNode prepared=preparedPayload(attempt);
        String value=prepared==null?null:prepared.path("disclosureText").asText(null);
        if(value==null||value.isBlank()) {metric("disclosure_fallback");return FALLBACK_DISCLOSURE;}
        return value;
    }
    public String languageTag(RecruitmentInterviewCallAttempt attempt) {
        JsonNode prepared=preparedPayload(attempt);
        String value=prepared==null?null:prepared.path("sections").path(0).path("languageTag").asText(null);
        if("vi-VN".equals(value)||"en-US".equals(value))return value;
        metric("language_fallback");return "en-US";
    }
    public boolean isTerminal(RecruitmentInterviewCallAttempt attempt){return terminal(attempt.getStatus());}
    public String runtimeToken(RecruitmentInterviewCallAttempt attempt) {
        String token=RecruitmentRuntimeToken.derive(properties.runtimeTokenSecret(),attempt.getId(),
                attempt.getPreparedSessionSha256());
        if(attempt.getRuntimeTokenSha256()==null||!MessageDigest.isEqual(
                attempt.getRuntimeTokenSha256().getBytes(StandardCharsets.US_ASCII),sha256(token).getBytes(StandardCharsets.US_ASCII))
                ||attempt.getRuntimeTokenExpiresAt()==null
                ||!attempt.getRuntimeTokenExpiresAt().isAfter(clock.instant()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"INVALID_RUNTIME_TOKEN");
        return token;
    }

    private void applyStatus(RecruitmentInterviewCallAttempt attempt,String raw) {
        String status=safe(raw).toLowerCase(Locale.ROOT);
        RecruitmentInterview interview=interviews.findForUpdate(attempt.getTenantId(),attempt.getInterviewId()).orElseThrow();
        switch(status) {
            case "initiated","queued" -> {attempt.setStatus(CallAttemptStatus.CALLING);interview.setStatus(InterviewStatus.CALLING);}
            case "ringing" -> {attempt.setStatus(CallAttemptStatus.RINGING);interview.setStatus(InterviewStatus.RINGING);}
            case "in-progress","answered" -> {attempt.setStatus(CallAttemptStatus.CONSENT_PENDING);
                if(attempt.getAnsweredAt()==null)attempt.setAnsweredAt(clock.instant());interview.setStatus(InterviewStatus.CONSENT_PENDING);}
            case "completed" -> transportCompleted(attempt,interview);
            case "busy","no-answer" -> terminal(attempt,CallAttemptStatus.NO_ANSWER,InterviewStatus.NO_ANSWER,
                    "TWILIO_"+status.toUpperCase(Locale.ROOT).replace('-','_'),true);
            case "canceled" -> terminal(attempt,CallAttemptStatus.CANCELLED,InterviewStatus.CANCELLED,"TWILIO_CANCELLED",true);
            case "failed" -> terminal(attempt,CallAttemptStatus.FAILED,InterviewStatus.FAILED,"TWILIO_FAILED",attempt.getAnsweredAt()==null);
            default -> { }
        }
        attempts.save(attempt);interviews.save(interview);
        if(projectionEvents!=null){String event=switch(interview.getStatus()){case NO_ANSWER->"interview.no_answer";case FAILED->"interview.failed";case CANCELLED->"interview.cancelled";default->null;};if(event!=null)projectionEvents.interview(interview,event);}
    }

    private void transportCompleted(RecruitmentInterviewCallAttempt attempt,RecruitmentInterview interview) {
        if(attempt.getConsentedAt()==null) {
            terminal(attempt,CallAttemptStatus.NO_ANSWER,InterviewStatus.NO_ANSWER,"CONSENT_NOT_RECEIVED",false);
            return;
        }
        attempt.setStatus(CallAttemptStatus.COMPLETED);attempt.setFailureCode(null);attempt.setTerminalAt(clock.instant());
        attempt.setNextRetryAt(clock.instant().plusSeconds(60));
        interview.setActiveCallAttemptId(null);
    }

    @Scheduled(fixedDelayString="${app.recruitment.calling.reconcile-delay-ms:10000}")
    @Transactional
    public void reconcileCompletedTransportWithoutResult() {
        List<UUID> reconciled=transportReconciliation.failCompletedTransportsWithoutResult();
        for(UUID interviewId:reconciled)interviews.findById(interviewId).ifPresent(interview->
                projectionEvents.interview(interview,"interview.failed"));
        if(!reconciled.isEmpty())metrics.counter("recruitment.interview.transport_reconciliation",
                "result","missing_runtime_result").increment(reconciled.size());
    }

    @Scheduled(fixedDelayString="${app.recruitment.calling.reconcile-delay-ms:10000}")
    @Transactional
    public void reconcileStalePreAnswerCalls() {
        LocalDateTime staleBefore=LocalDateTime.now(clock).minusSeconds(STALE_PRE_ANSWER_SECONDS);
        List<RecruitmentInterviewCallAttempt> stale=attempts.lockStalePreAnswer(staleBefore);
        for(RecruitmentInterviewCallAttempt attempt:stale) {
            if(terminal(attempt.getStatus()))continue;
            terminal(attempt,CallAttemptStatus.FAILED,InterviewStatus.FAILED,
                    "TWILIO_CALLBACK_ERROR",true);
        }
        if(!stale.isEmpty())metrics.counter("recruitment.interview.transport_reconciliation",
                "result","missing_pre_answer_callback").increment(stale.size());
    }

    private void settleTerminalDuration(RecruitmentInterviewCallAttempt attempt,String rawDuration) {
        long duration=parseDuration(rawDuration);
        attempt.setCallDurationSeconds((int)Math.min(14400,duration));
        RecruitmentInterview interview=interviews.findForUpdate(attempt.getTenantId(),attempt.getInterviewId()).orElseThrow();
        if(attempt.getAnsweredAt()==null&&duration==0)return;
        if(interview.getQuotaReservationId()==null||interview.getQuotaReservedSeconds()==null)return;
        long settled=Math.min(duration,interview.getQuotaReservedSeconds());
        if(settled<duration)metrics.counter("recruitment.interview.quota_settlement","result","clamped").increment();
        try {
            HiringQuotaApi.Consumption result=quota.settleInterviewSeconds(
                    interview.getTenantId(),interview.getQuotaReservationId(),settled);
            metrics.counter("recruitment.interview.quota_settlement","result",
                    result.idempotentReplay()?"replay":"settled").increment();
        } catch(HiringQuotaApi.HiringQuotaException exception) {
            String result="CONFLICTING_REPLAY".equals(exception.getCode())?"conflicting_duration":"quota_error";
            metrics.counter("recruitment.interview.quota_settlement","result",result).increment();
            HttpStatus status="CONFLICTING_REPLAY".equals(exception.getCode())?HttpStatus.CONFLICT:HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(status,exception.getCode());
        }
    }

    private void terminal(RecruitmentInterviewCallAttempt attempt,CallAttemptStatus attemptStatus,
            InterviewStatus interviewStatus,String code,boolean release) {
        if(attempt.getConsentedAt()==null)cancelPreparedRuntime(attempt,code);
        Instant now=clock.instant();attempt.setStatus(attemptStatus);attempt.setFailureCode(code);attempt.setTerminalAt(now);
        RecruitmentInterview interview=interviews.findForUpdate(attempt.getTenantId(),attempt.getInterviewId()).orElseThrow();
        interview.setStatus(interviewStatus);interview.setActiveCallAttemptId(null);interview.setCompletedAt(LocalDateTime.now(clock));
        attempts.save(attempt);interviews.save(interview);
        if(projectionEvents!=null){String event=switch(interviewStatus){case NO_ANSWER->"interview.no_answer";case FAILED->"interview.failed";case CANCELLED->"interview.cancelled";case DECLINED->"interview.declined";default->null;};if(event!=null)projectionEvents.interview(interview,event);}
        if(release&&attempt.getAnsweredAt()==null&&interview.getQuotaReservationId()!=null)try {
            quota.releaseInterviewSeconds(interview.getTenantId(),interview.getQuotaReservationId());
        } catch(HiringQuotaApi.HiringQuotaException ignored) {}
    }

    private void cancelPreparedRuntime(RecruitmentInterviewCallAttempt attempt,String reason) {
        if(attempt.getPreparedSessionSha256()==null)return;
        for(int number=1;number<=properties.cancellationMaxAttempts();number++) {
            attempt.setCancellationAttempts(number);attempts.save(attempt);
            try {
                inference.cancel(new InterviewInferenceApi.CancelInterviewCommand(
                        attempt.getSessionId(),attempt.getId(),reason,null));return;
            } catch(RuntimeException ignored) {}
        }
    }

    private void record(RecruitmentInterviewCallAttempt attempt,TwilioCallbackKind kind,Long sequence,
            String semantic,MultiValueMap<String,String> form,TwilioCallbackResult result) {
        String digest=payloadHash(form);
        var existing=inbox.findByCallAttemptIdAndCallbackKindAndSemanticKey(attempt.getId(),kind,semantic);
        if(existing.isPresent()) {
            if(existing.get().getPayloadSha256().equals(digest)){metric("duplicate");return;}
            existing.get().setProcessingResult(TwilioCallbackResult.REJECTED_CONFLICT);inbox.save(existing.get());
            throw new ResponseStatusException(HttpStatus.CONFLICT,"TWILIO_CALLBACK_CONFLICT");
        }
        RecruitmentTwilioCallbackInbox item=new RecruitmentTwilioCallbackInbox();item.setTenantId(attempt.getTenantId());
        item.setCallAttemptId(attempt.getId());item.setTwilioCallSid(attempt.getTwilioCallSid());item.setCallbackKind(kind);
        item.setSequenceNumber(sequence);item.setSemanticKey(semantic);item.setPayloadSha256(digest);
        item.setProcessingResult(result);item.setProcessedAt(LocalDateTime.now(clock));inbox.save(item);
    }

    private String payloadHash(MultiValueMap<String,String> form) {
        try {TreeMap<String,List<String>> values=new TreeMap<>();form.forEach((key,value)->values.put(key,List.copyOf(value)));
            return sha256(mapper.writeValueAsString(values));}catch(Exception exception){throw new IllegalStateException(exception);}
    }
    private void rejectBinding(){metric("binding_rejected");throw new ResponseStatusException(HttpStatus.CONFLICT,"TWILIO_CALL_BINDING_MISMATCH");}
    private JsonNode preparedPayload(RecruitmentInterviewCallAttempt attempt) {
        try {
            String value=attempt==null?null:attempt.getPreparedSession();
            return value==null||value.isBlank()?null:mapper.readTree(value);
        } catch(Exception ignored) {return null;}
    }
    private void metric(String result){metrics.counter("recruitment.twilio.callback","result",result).increment();}
    private static long parseSequence(String value){try{long result=Long.parseLong(value);if(result<0)throw new NumberFormatException();return result;}
        catch(RuntimeException exception){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_SEQUENCE_NUMBER");}}
    private static long parseDuration(String value){try{long result=Long.parseLong(value);if(result<0)throw new NumberFormatException();return result;}
        catch(RuntimeException exception){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_CALL_DURATION");}}
    private static boolean terminalTwilioStatus(String status){return Set.of("completed","busy","no-answer","canceled","failed").contains(status);}
    private static boolean terminal(CallAttemptStatus status){return Set.of(CallAttemptStatus.COMPLETED,CallAttemptStatus.NO_ANSWER,
            CallAttemptStatus.DECLINED,CallAttemptStatus.FAILED,CallAttemptStatus.CANCELLED,CallAttemptStatus.EXPIRED).contains(status);}
    private static String safe(String value){return value==null?"":value;}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception exception){throw new IllegalStateException(exception);}}
}
