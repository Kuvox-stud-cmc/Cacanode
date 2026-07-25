package com.cacanode.api.recruitment.service;

import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CallAttemptStatus;
import com.cacanode.api.recruitment.model.RecruitmentInterview;
import com.cacanode.api.recruitment.model.RecruitmentInterviewCallAttempt;
import com.cacanode.api.recruitment.repository.RecruitmentInterviewCallAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="calling-enabled",havingValue="true")
public class RecruitmentCallCancellationCoordinator {
    private static final Set<CallAttemptStatus> TERMINAL=Set.of(CallAttemptStatus.COMPLETED,
            CallAttemptStatus.NO_ANSWER,CallAttemptStatus.DECLINED,CallAttemptStatus.FAILED,
            CallAttemptStatus.CANCELLED,CallAttemptStatus.EXPIRED);
    private final RecruitmentInterviewCallAttemptRepository attempts;
    private final InterviewInferenceApi inference;
    private final TwilioCallTransport twilio;
    private final RecruitmentCallingProperties properties;
    private final Clock clock;

    @Transactional
    public boolean cancel(RecruitmentInterview interview,String reason) {
        if(interview.getActiveCallAttemptId()==null)return false;
        RecruitmentInterviewCallAttempt attempt=attempts.findForUpdate(interview.getActiveCallAttemptId()).orElse(null);
        if(attempt==null)return false;
        boolean answered=attempt.getAnsweredAt()!=null;
        if(!TERMINAL.contains(attempt.getStatus())) {
            for(int number=1;number<=properties.cancellationMaxAttempts();number++) {
                attempt.setCancellationAttempts(number);attempts.save(attempt);
                try {inference.cancel(new InterviewInferenceApi.CancelInterviewCommand(attempt.getSessionId(),
                        attempt.getId(),reason,null));break;}catch(RuntimeException ignored) {}
            }
            if(attempt.getTwilioCallSid()!=null)for(int number=1;number<=properties.cancellationMaxAttempts();number++) {
                attempt.setTerminationAttempts(number);attempts.save(attempt);if(twilio.terminate(attempt.getTwilioCallSid()))break;
            }
            attempt.setStatus(CallAttemptStatus.CANCELLED);attempt.setFailureCode("INTERVIEW_CANCELLED");
            attempt.setCancelledAt(clock.instant());attempt.setTerminalAt(clock.instant());attempt.setNextRetryAt(null);
            attempt.setCreateOutcomeUncertain(false);attempt.setCreateUncertainUntil(null);attempts.save(attempt);
        }
        interview.setActiveCallAttemptId(null);return answered;
    }
}
