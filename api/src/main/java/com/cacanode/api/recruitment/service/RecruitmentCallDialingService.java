package com.cacanode.api.recruitment.service;

import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.ai.api.InterviewInferenceException;
import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="calling-enabled",havingValue="true")
public class RecruitmentCallDialingService {
    private static final List<CallAttemptStatus> CONCURRENT=List.of(CallAttemptStatus.DIALING,
            CallAttemptStatus.CALLING,CallAttemptStatus.RINGING,CallAttemptStatus.CONSENT_PENDING,
            CallAttemptStatus.IN_PROGRESS);
    private final RecruitmentInterviewRepository interviews;
    private final RecruitmentInterviewCallAttemptRepository attempts;
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentCandidateRepository candidates;
    private final RecruitmentJobRepository jobs;
    private final RecruitmentCvAnalysisRepository analyses;
    private final InterviewSessionSnapshotFactory snapshots;
    private final InterviewInferenceApi inference;
    private final TwilioCallTransport twilio;
    private final HiringQuotaApi quota;
    private final RecruitmentCallingProperties properties;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final RecruitmentCapabilityService capabilities;
    private final RecruitmentCallFraudGuard fraudGuard;
    @Autowired(required=false) private RecruitmentProjectionEventPublisher projectionEvents;

    @Scheduled(fixedDelayString="${app.recruitment.calling.scheduler-delay-ms:1000}")
    @Transactional
    public void dialDue() {
        if(!properties.schedulerEnabled()||!interviews.tryCallSchedulerLock())return;
        Instant now=clock.instant();
        for(RecruitmentInterview interview:interviews.lockDueForCalling(
                now.minusSeconds(properties.claimLateSeconds()),now.plusSeconds(properties.claimEarlySeconds()))) {
            if(!capabilities.capabilities(interview.getTenantId()).callingEnabled())continue;
            RecruitmentInterviewCallAttempt attempt=claim(interview,false);
            if(attempt!=null&&attempt.getStatus()==CallAttemptStatus.PREPARING)prepareAndDial(interview,attempt);
        }
        for(RecruitmentInterview interview:interviews.lockMissedForCalling(
                now.minusSeconds(properties.claimLateSeconds())))expireMissed(interview);
    }

    @Scheduled(fixedDelayString="${app.recruitment.calling.reconcile-delay-ms:10000}")
    @Transactional
    public void reconcileUncertainCreates() {
        Instant now=clock.instant();
        for(RecruitmentInterviewCallAttempt attempt:attempts.lockExpiredUncertain(now)) {
            if(attempt.getTwilioCallSid()!=null) {
                attempt.setCreateOutcomeUncertain(false);attempt.setCreateUncertainUntil(null);
                attempt.setFailureCode(null);attempt.setStatus(CallAttemptStatus.CALLING);attempts.save(attempt);continue;
            }
            RecruitmentInterview interview=interviews.findForUpdate(attempt.getTenantId(),attempt.getInterviewId()).orElse(null);
            if(interview!=null)fail(interview,attempt,"TWILIO_CREATE_UNCONFIRMED",true);
        }
    }

    @Transactional
    public RecruitmentDtos.DialResponse dial(UUID tenantId,UUID interviewId) {
        capabilities.requireCalling(tenantId);
        RecruitmentInterview interview=interviews.findForUpdate(tenantId,interviewId).orElseThrow();
        if(interview.getActiveCallAttemptId()!=null) {
            RecruitmentInterviewCallAttempt existing=attempts.findForUpdate(interview.getActiveCallAttemptId()).orElseThrow();
            return response(existing);
        }
        RecruitmentInterviewCallAttempt attempt=claim(interview,true);
        if(attempt.getStatus()==CallAttemptStatus.PREPARING)prepareAndDial(interview,attempt);
        return response(attempt);
    }

    private RecruitmentInterviewCallAttempt claim(RecruitmentInterview interview,boolean persistFailure) {
        RecruitmentApplication application=applications.findByIdAndTenantId(interview.getApplicationId(),interview.getTenantId()).orElse(null);
        RecruitmentJob job=jobs.findByIdAndTenantId(interview.getJobId(),interview.getTenantId()).orElse(null);
        RecruitmentCandidate candidate=application==null?null:candidates.findByIdAndTenantId(
                application.getCandidateId(),interview.getTenantId()).orElse(null);
        String failure=eligibility(interview,application,job,candidate);
        if(failure==null&&attempts.countGlobalActive(CONCURRENT)>=properties.globalConcurrency())failure="GLOBAL_CONCURRENCY_LIMIT";
        if(failure==null&&attempts.countTenantActive(interview.getTenantId(),CONCURRENT)>=properties.tenantConcurrency())failure="TENANT_CONCURRENCY_LIMIT";
        String destination=candidate==null?null:candidate.getPhone();
        if(failure==null)try{destination=fraudGuard.requireAttempt(interview.getTenantId(),destination);}
        catch(com.cacanode.api.common.exception.custom.ConflictException rejected){failure=rejected.getMessage();}
        if(failure!=null&&!persistFailure){metric("claim_rejected",failure);return null;}

        RecruitmentInterviewCallAttempt attempt=new RecruitmentInterviewCallAttempt();
        attempt.setTenantId(interview.getTenantId());attempt.setInterviewId(interview.getId());
        attempt.setApplicationId(interview.getApplicationId());attempt.setJobId(interview.getJobId());
        attempt.setSessionId(interview.getId());attempt.setScheduleVersion(interview.getScheduleVersion());
        attempt.setAttemptNumber(attempts.maxAttemptNumber(interview.getId())+1);
        attempt.setTemplateRevisionId(interview.getTemplateRevisionId());
        attempt.setDestinationE164(destination);
        if(failure!=null) {
            attempt.setStatus(CallAttemptStatus.FAILED);attempt.setFailureCode(failure);
            attempt.setTerminalAt(clock.instant());attempts.save(attempt);metric("claim_rejected",failure);return attempt;
        }
        attempt.setStatus(CallAttemptStatus.PREPARING);attempt=attempts.saveAndFlush(attempt);
        RecruitmentCvAnalysis analysis=application.getActiveCvAnalysisId()==null?null:
                analyses.findByIdAndTenantId(application.getActiveCvAnalysisId(),interview.getTenantId()).orElse(null);
        InterviewSessionSnapshotFactory.Snapshot snapshot=snapshots.build(interview,job,candidate,analysis,attempt.getId());
        attempt.setPreparedSession(snapshot.json());attempt.setPreparedSessionSha256(snapshot.sha256());
        attempt.setPreparedSnapshotVersion(InterviewSessionSnapshotFactory.SNAPSHOT_VERSION);
        if(analysis!=null&&snapshot.cvPersonalizationEnabled()) {
            attempt.setCvAnalysisId(analysis.getId());attempt.setCvAnalysisSha256(analysis.getOutcomePayloadSha256());
        }
        attempts.save(attempt);interview.setActiveCallAttemptId(attempt.getId());
        interview.setStatus(InterviewStatus.PREPARING);interviews.save(interview);metric("claimed","OK");return attempt;
    }

    private String eligibility(RecruitmentInterview interview,RecruitmentApplication application,
            RecruitmentJob job,RecruitmentCandidate candidate) {
        if(interview.getStatus()!=InterviewStatus.SCHEDULED)return "INTERVIEW_NOT_SCHEDULED";
        Instant now=clock.instant();
        if(interview.getScheduledStartAt()==null||interview.getScheduledStartAt().isBefore(
                now.minusSeconds(properties.claimLateSeconds()))||interview.getScheduledStartAt().isAfter(
                now.plusSeconds(properties.claimEarlySeconds())))return "OUTSIDE_DIAL_WINDOW";
        if(application==null||application.getStatus()!=ApplicationStatus.INTERVIEW_SCHEDULED)return "APPLICATION_NOT_SCHEDULED";
        if(job==null||(job.getStatus()!=JobStatus.PUBLISHED&&job.getStatus()!=JobStatus.PAUSED))return "JOB_NOT_CALLABLE";
        if(candidate==null||candidate.getPhone()==null||!candidate.getPhone().matches("^\\+84[0-9]{9,10}$"))return "INVALID_DESTINATION";
        if(interview.getQuotaReservationId()==null||interview.getQuotaReservedSeconds()==null||
                !quota.isInterviewReservationActive(interview.getTenantId(),interview.getQuotaReservationId(),
                        interview.getQuotaReservedSeconds()))return "QUOTA_RESERVATION_INVALID";
        return null;
    }

    private void expireMissed(RecruitmentInterview interview) {
        RecruitmentApplication application=applications.findByIdAndTenantId(interview.getApplicationId(),interview.getTenantId()).orElse(null);
        RecruitmentCandidate candidate=application==null?null:candidates.findByIdAndTenantId(
                application.getCandidateId(),interview.getTenantId()).orElse(null);
        RecruitmentInterviewCallAttempt attempt=new RecruitmentInterviewCallAttempt();
        attempt.setTenantId(interview.getTenantId());attempt.setInterviewId(interview.getId());
        attempt.setApplicationId(interview.getApplicationId());attempt.setJobId(interview.getJobId());
        attempt.setSessionId(interview.getId());attempt.setScheduleVersion(interview.getScheduleVersion());
        attempt.setAttemptNumber(attempts.maxAttemptNumber(interview.getId())+1);
        attempt.setTemplateRevisionId(interview.getTemplateRevisionId());
        attempt.setDestinationE164(candidate==null?null:candidate.getPhone());attempt.setStatus(CallAttemptStatus.EXPIRED);
        attempt.setFailureCode("DIAL_WINDOW_EXPIRED");attempt.setTerminalAt(clock.instant());
        attempt.setExpiresAt(clock.instant());attempts.save(attempt);interview.setStatus(InterviewStatus.EXPIRED);
        interview.setExpiredAt(LocalDateTime.now(clock));interview=interviews.save(interview);
        if(projectionEvents!=null)projectionEvents.interview(interview,"interview.expired");
        if(interview.getQuotaReservationId()!=null)try {
            quota.releaseInterviewSeconds(interview.getTenantId(),interview.getQuotaReservationId());
        } catch(HiringQuotaApi.HiringQuotaException ignored) {}
        metric("expired","DIAL_WINDOW_EXPIRED");
    }

    private void prepareAndDial(RecruitmentInterview interview,RecruitmentInterviewCallAttempt attempt) {
        InterviewInferenceApi.PreparedInterview prepared=null;
        for(int number=1;number<=properties.preparationMaxAttempts();number++) {
            attempt.setPreparationAttempts(number);attempts.save(attempt);
            try {prepared=inference.prepare(command(attempt));break;}
            catch(InterviewInferenceException exception) {
                if(number==properties.preparationMaxAttempts()) {
                    fail(interview,attempt,bounded(exception.getCode(),"PREPARATION_FAILED"),true);return;
                }
                boundedBackoff(number);
            }
        }
        if(prepared==null||!prepared.sessionId().equals(attempt.getSessionId())
                ||!prepared.callAttemptId().equals(attempt.getId())
                ||!prepared.acceptedSnapshotSha256().equals(attempt.getPreparedSessionSha256())
                ||prepared.runtimeToken().isBlank()||!prepared.expiresAt().isAfter(clock.instant())) {
            fail(interview,attempt,"INVALID_PREPARATION_RESPONSE",true);return;
        }
        String runtimeToken=RecruitmentRuntimeToken.derive(properties.runtimeTokenSecret(),attempt.getId(),
                attempt.getPreparedSessionSha256());
        if(!MessageDigest.isEqual(runtimeToken.getBytes(StandardCharsets.US_ASCII),
                prepared.runtimeToken().getBytes(StandardCharsets.US_ASCII))) {
            fail(interview,attempt,"RUNTIME_TOKEN_DERIVATION_MISMATCH",true);return;
        }
        attempt.setRuntimeTokenSha256(sha256(runtimeToken));
        attempt.setRuntimeTokenExpiresAt(prepared.expiresAt());attempt.setStatus(CallAttemptStatus.READY);
        attempts.save(attempt);
        attempt.setStatus(CallAttemptStatus.DIALING);attempts.saveAndFlush(attempt);
        String base=properties.callbackBaseUrl().replaceAll("/+$","");
        String attemptQuery="attempt="+attempt.getId();
        String voice=base+"/api/v1/public/twilio/interviews/voice?"+attemptQuery;
        try {
            TwilioCallTransport.CreatedCall call=twilio.create(new TwilioCallTransport.CreateCall(
                    attempt.getDestinationE164(),voice,
                    base+"/api/v1/public/twilio/interviews/fallback?"+attemptQuery,
                    base+"/api/v1/public/twilio/interviews/status?"+attemptQuery,duration(attempt)));
            if(call.callSid()==null||!call.callSid().matches("^CA[0-9a-fA-F]{32}$")) {
                fail(interview,attempt,"INVALID_TWILIO_RESPONSE",true);return;
            }
            attempt.setTwilioCallSid(call.callSid());attempt.setStatus(CallAttemptStatus.CALLING);
            attempt.setCreateOutcomeUncertain(false);attempt.setCreateUncertainUntil(null);attempts.save(attempt);
            interview.setStatus(InterviewStatus.CALLING);interviews.save(interview);metric("dial_created","OK");
        } catch (TwilioCallTransport.DefiniteFailure exception) {
            fail(interview,attempt,"TWILIO_CREATE_REJECTED",true);
        } catch (TwilioCallTransport.UncertainFailure exception) {
            attempt.setCreateOutcomeUncertain(true);attempt.setCreateUncertainUntil(clock.instant().plusSeconds(120));
            attempt.setFailureCode("TWILIO_CREATE_UNCERTAIN");attempts.save(attempt);metric("dial_uncertain","TWILIO_CREATE_UNCERTAIN");
        }
    }

    private InterviewInferenceApi.PrepareInterviewCommand command(RecruitmentInterviewCallAttempt attempt) {
        try {
            JsonNode root=mapper.readTree(attempt.getPreparedSession());
            List<InterviewInferenceApi.SectionSnapshot> sections=new ArrayList<>();
            for(JsonNode section:root.path("sections")) {
                List<InterviewInferenceApi.QuestionSnapshot> questions=new ArrayList<>();
                for(JsonNode q:section.path("questions"))questions.add(new InterviewInferenceApi.QuestionSnapshot(
                        UUID.fromString(q.path("questionId").asText()),q.path("position").asInt(),q.path("prompt").asText(),
                        q.path("competency").asText(),q.path("rubric").asText(),q.path("followUpLimit").asInt(),
                        InterviewInferenceApi.QuestionSource.valueOf(q.path("source").asText()),
                        q.has("evidence")?q.path("evidence").asText():null));
                sections.add(new InterviewInferenceApi.SectionSnapshot(UUID.fromString(section.path("sectionId").asText()),
                        section.path("position").asInt(),InterviewInferenceApi.SectionKind.valueOf(section.path("kind").asText()),
                        section.path("languageTag").asText(),section.path("durationLimitSeconds").asInt(),
                        section.path("transitionText").asText(),questions));
            }
            JsonNode limits=root.path("interactionLimits");
            return new InterviewInferenceApi.PrepareInterviewCommand(attempt.getSessionId(),attempt.getId(),attempt.getTenantId(),
                    attempt.getTemplateRevisionId(),attempt.getPreparedSnapshotVersion(),attempt.getPreparedSessionSha256(),
                    root.path("companyDisplayName").asText(),root.path("candidateDisplayName").asText(),
                    root.path("introductionText").asText(),root.path("disclosureText").asText(),root.path("closingText").asText(),
                    root.path("durationLimitSeconds").asInt(),new InterviewInferenceApi.InteractionLimits(
                    limits.path("repetitionLimit").asInt(),limits.path("clarificationLimit").asInt(),
                    limits.path("silenceTimeoutSeconds").asInt(),limits.path("silencePromptLimit").asInt()),false,
                    root.path("cvPersonalizationEnabled").asBoolean(),sections,null);
        } catch (Exception exception) {throw new IllegalStateException("Prepared interview snapshot is invalid",exception);}
    }

    private int duration(RecruitmentInterviewCallAttempt attempt){try{return mapper.readTree(attempt.getPreparedSession())
            .path("durationLimitSeconds").asInt();}catch(Exception exception){return 900;}}

    private void fail(RecruitmentInterview interview,RecruitmentInterviewCallAttempt attempt,String code,boolean releaseQuota) {
        Instant now=clock.instant();attempt.setStatus(CallAttemptStatus.FAILED);attempt.setFailureCode(bounded(code,"CALL_FAILED"));
        attempt.setTerminalAt(now);attempt.setNextRetryAt(null);attempt.setCreateOutcomeUncertain(false);
        attempt.setCreateUncertainUntil(null);attempts.save(attempt);interview.setActiveCallAttemptId(null);
        interview.setStatus(InterviewStatus.FAILED);interview=interviews.save(interview);
        if(projectionEvents!=null)projectionEvents.interview(interview,"interview.failed");
        if(releaseQuota&&attempt.getAnsweredAt()==null&&interview.getQuotaReservationId()!=null) {
            try {quota.releaseInterviewSeconds(interview.getTenantId(),interview.getQuotaReservationId());}
            catch(HiringQuotaApi.HiringQuotaException ignored) {}
        }
        metric("failed",attempt.getFailureCode());
    }

    private RecruitmentDtos.DialResponse response(RecruitmentInterviewCallAttempt attempt) {
        return new RecruitmentDtos.DialResponse(attempt.getId(),attempt.getStatus(),attempt.getFailureCode(),clock.instant());
    }
    private void metric(String outcome,String code){metrics.counter("recruitment.interview.dial","outcome",outcome,
            "code",bounded(code,"UNKNOWN")).increment();}
    private static String bounded(String value,String fallback){if(value==null||value.isBlank())return fallback;
        return value.length()>100?value.substring(0,100):value;}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception exception){throw new IllegalStateException(exception);}}
    private static void boundedBackoff(int attempt){try{Thread.sleep(attempt==1?100:250);}catch(InterruptedException exception){Thread.currentThread().interrupt();}}
}
