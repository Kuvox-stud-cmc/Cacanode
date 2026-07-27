package com.cacanode.api.recruitment.service;

import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.common.event.durable.DurableEventPublisher;
import com.cacanode.api.recruitment.api.event.RecruitmentInterviewProjectionChangedEvent;
import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CallAttemptStatus;
import com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus;
import com.cacanode.api.recruitment.model.RecruitmentInterview;
import com.cacanode.api.recruitment.model.RecruitmentInterviewCallAttempt;
import com.cacanode.api.recruitment.repository.RecruitmentInterviewCallAttemptRepository;
import com.cacanode.api.recruitment.repository.RecruitmentInterviewRepository;
import com.cacanode.api.recruitment.repository.RecruitmentInterviewTransportReconciliationRepository;
import com.cacanode.api.recruitment.repository.RecruitmentTwilioCallbackInboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TwilioCallbackSettlementTest {
    private final RecruitmentInterviewCallAttemptRepository attempts=mock(RecruitmentInterviewCallAttemptRepository.class);
    private final RecruitmentTwilioCallbackInboxRepository inbox=mock(RecruitmentTwilioCallbackInboxRepository.class);
    private final RecruitmentInterviewRepository interviews=mock(RecruitmentInterviewRepository.class);
    private final HiringQuotaApi quota=mock(HiringQuotaApi.class);
    private final InterviewInferenceApi inference=mock(InterviewInferenceApi.class);
    private final RecruitmentInterviewTransportReconciliationRepository reconciliation=
            mock(RecruitmentInterviewTransportReconciliationRepository.class);
    private final DurableEventPublisher durableEvents=mock(DurableEventPublisher.class);
    private final RecruitmentProjectionEventPublisher projectionEvents=
            new RecruitmentProjectionEventPublisher(durableEvents);
    private final SimpleMeterRegistry metrics=new SimpleMeterRegistry();
    private final UUID tenant=UUID.randomUUID();
    private final UUID interviewId=UUID.randomUUID();
    private final UUID attemptId=UUID.randomUUID();
    private final UUID reservationId=UUID.randomUUID();
    private RecruitmentInterviewCallAttempt attempt;
    private RecruitmentInterview interview;
    private TwilioCallbackService service;

    @BeforeEach
    void setUp(){
        attempt=new RecruitmentInterviewCallAttempt();attempt.setId(attemptId);attempt.setTenantId(tenant);
        attempt.setInterviewId(interviewId);attempt.setTwilioCallSid("CA"+"1".repeat(32));
        attempt.setStatus(CallAttemptStatus.IN_PROGRESS);attempt.setAnsweredAt(Instant.parse("2026-01-01T00:00:00Z"));
        attempt.setConsentedAt(Instant.parse("2026-01-01T00:00:01Z"));
        interview=new RecruitmentInterview();interview.setId(interviewId);interview.setTenantId(tenant);
        interview.setApplicationId(UUID.randomUUID());interview.setJobId(UUID.randomUUID());
        interview.setStatus(InterviewStatus.IN_PROGRESS);
        interview.setQuotaReservationId(reservationId);interview.setQuotaReservedSeconds(120L);
        when(attempts.findForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(interviews.findForUpdate(tenant,interviewId)).thenReturn(Optional.of(interview));
        when(inbox.findByCallAttemptIdAndCallbackKindAndSemanticKey(any(),any(),any())).thenReturn(Optional.empty());
        when(quota.settleInterviewSeconds(any(),any(),anyLong()))
                .thenReturn(new HiringQuotaApi.Consumption(120,0,false));
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        service=new TwilioCallbackService(properties,attempts,inbox,interviews,
                quota,inference,new ObjectMapper(),metrics,
                Clock.fixed(Instant.parse("2026-01-01T00:05:00Z"), ZoneOffset.UTC),reconciliation,
                projectionEvents);
    }

    @ParameterizedTest
    @EnumSource(value=CallAttemptStatus.class,names={"DECLINED","CANCELLED","FAILED","EXPIRED","NO_ANSWER"})
    void lateTerminalCallbackSettlesClampedDurationWithoutReopeningState(CallAttemptStatus state){
        attempt.setStatus(state);
        service.status(attemptId,terminal("completed","999","8"));
        verify(quota).settleInterviewSeconds(tenant,reservationId,120);
        assertEquals(state,attempt.getStatus());
    }

    @Test
    void olderTerminalCallbackStillSettlesConnectedCall(){
        attempt.setCallbackSequence(20);
        service.status(attemptId,terminal("completed","30","4"));
        verify(quota).settleInterviewSeconds(tenant,reservationId,30);
        assertEquals(CallAttemptStatus.IN_PROGRESS,attempt.getStatus());
    }

    @Test
    void unansweredTerminalCallbackKeepsReleasePath(){
        attempt.setAnsweredAt(null);
        service.status(attemptId,terminal("no-answer","0","1"));
        verify(quota,never()).settleInterviewSeconds(any(),any(),anyLong());
        verify(quota).releaseInterviewSeconds(tenant,reservationId);
    }

    @Test
    void malformedPreparedPayloadFallsBackToSafeConsentCopyAndLanguage() {
        attempt.setPreparedSession("not-json");

        assertEquals("en-US",service.languageTag(attempt));
        assertEquals("This is an automated AI interview. Your responses will be processed for recruitment "
                +"and may be recorded when recording is enabled.",service.disclosure(attempt));
    }

    @Test
    void exactDurationReplayUsesQuotaIdempotencyWithoutReopening(){
        when(quota.settleInterviewSeconds(tenant,reservationId,30))
                .thenReturn(new HiringQuotaApi.Consumption(30,90,false),
                        new HiringQuotaApi.Consumption(30,90,true));
        service.status(attemptId,terminal("completed","30","1"));
        service.status(attemptId,terminal("completed","30","2"));
        verify(quota,times(2)).settleInterviewSeconds(tenant,reservationId,30);
        assertEquals(CallAttemptStatus.COMPLETED,attempt.getStatus());
    }

    @Test
    void twilioCompletedTerminalizesTransportButWaitsForRuntimeBusinessResult(){
        attempt.setStatus(CallAttemptStatus.IN_PROGRESS);attempt.setAnsweredAt(Instant.parse("2026-01-01T00:00:00Z"));
        interview.setStatus(InterviewStatus.IN_PROGRESS);interview.setActiveCallAttemptId(attemptId);
        service.status(attemptId,terminal("completed","42","8"));
        assertEquals(CallAttemptStatus.COMPLETED,attempt.getStatus());
        assertEquals(InterviewStatus.IN_PROGRESS,interview.getStatus());
        assertNull(interview.getActiveCallAttemptId());
        assertEquals(Instant.parse("2026-01-01T00:06:00Z"),attempt.getNextRetryAt());
    }

    @Test
    void reconcilesTransportCompletionWhenNoRuntimeResultArrives() {
        interview.setStatus(InterviewStatus.FAILED);
        when(reconciliation.failCompletedTransportsWithoutResult()).thenReturn(List.of(interviewId));
        when(interviews.findById(interviewId)).thenReturn(Optional.of(interview));

        service.reconcileCompletedTransportWithoutResult();

        verify(reconciliation).failCompletedTransportsWithoutResult();
        var payload=org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(durableEvents).publish(eq("recruitment.interview.projection.v1"),eq(1),payload.capture());
        RecruitmentInterviewProjectionChangedEvent event=
                assertInstanceOf(RecruitmentInterviewProjectionChangedEvent.class,payload.getValue());
        assertEquals(interviewId,event.interviewId());
        assertEquals("interview.failed",event.businessEvent());
        assertEquals(1.0,metrics.get("recruitment.interview.transport_reconciliation")
                .tag("result","missing_runtime_result").counter().count());
    }

    @Test
    void twilioCompletedBeforeConsentDoesNotLeaveInterviewStuck() {
        attempt.setStatus(CallAttemptStatus.CONSENT_PENDING);
        attempt.setAnsweredAt(Instant.parse("2026-01-01T00:00:00Z"));
        attempt.setConsentedAt(null);
        interview.setStatus(InterviewStatus.CONSENT_PENDING);interview.setActiveCallAttemptId(attemptId);

        service.status(attemptId,terminal("completed","15","8"));

        assertEquals(CallAttemptStatus.NO_ANSWER,attempt.getStatus());
        assertEquals("CONSENT_NOT_RECEIVED",attempt.getFailureCode());
        assertEquals(InterviewStatus.NO_ANSWER,interview.getStatus());
        assertNull(interview.getActiveCallAttemptId());
    }

    @Test
    void consentTimeoutIsNoAnswerButExplicitRejectionRemainsDeclined() {
        attempt.setStatus(CallAttemptStatus.CONSENT_PENDING);attempt.setConsentedAt(null);
        attempt.setSessionId(interviewId);attempt.setPreparedSessionSha256("a".repeat(64));
        interview.setStatus(InterviewStatus.CONSENT_PENDING);interview.setActiveCallAttemptId(attemptId);

        service.consent(attempt,false,"CONSENT_NOT_RECEIVED");

        assertEquals(CallAttemptStatus.NO_ANSWER,attempt.getStatus());
        assertEquals(InterviewStatus.NO_ANSWER,interview.getStatus());
        verify(inference).cancel(new InterviewInferenceApi.CancelInterviewCommand(
                interviewId,attemptId,"CONSENT_NOT_RECEIVED",null));

        attempt.setStatus(CallAttemptStatus.CONSENT_PENDING);attempt.setFailureCode(null);
        interview.setStatus(InterviewStatus.CONSENT_PENDING);interview.setActiveCallAttemptId(attemptId);
        service.consent(attempt,false,"CONSENT_DECLINED");

        assertEquals(CallAttemptStatus.DECLINED,attempt.getStatus());
        assertEquals(InterviewStatus.DECLINED,interview.getStatus());
    }

    @Test
    void conflictingLateDurationIsRejected(){
        when(quota.settleInterviewSeconds(tenant,reservationId,30))
                .thenReturn(new HiringQuotaApi.Consumption(30,90,false));
        when(quota.settleInterviewSeconds(tenant,reservationId,40))
                .thenThrow(new HiringQuotaApi.HiringQuotaException(
                        "CONFLICTING_REPLAY","different duration"));
        service.status(attemptId,terminal("completed","30","1"));
        ResponseStatusException exception=assertThrows(ResponseStatusException.class,
                ()->service.status(attemptId,terminal("completed","40","2")));
        assertEquals("CONFLICTING_REPLAY",exception.getReason());
        assertEquals(CallAttemptStatus.COMPLETED,attempt.getStatus());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={"-1","abc","9223372036854775808"})
    void invalidTerminalDurationIsRejected(String duration){
        ResponseStatusException exception=assertThrows(ResponseStatusException.class,
                ()->service.status(attemptId,terminal("completed",duration,"1")));
        assertEquals("INVALID_CALL_DURATION",exception.getReason());
        verifyNoInteractions(quota);
    }

    private LinkedMultiValueMap<String,String> terminal(String status,String duration,String sequence){
        LinkedMultiValueMap<String,String> form=new LinkedMultiValueMap<>();
        form.add("CallSid",attempt.getTwilioCallSid());form.add("CallStatus",status);
        form.add("SequenceNumber",sequence);if(duration!=null)form.add("CallDuration",duration);
        return form;
    }
}
