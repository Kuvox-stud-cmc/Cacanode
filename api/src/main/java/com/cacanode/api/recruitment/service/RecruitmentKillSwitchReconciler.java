package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.repository.RecruitmentInterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="calling-enabled",havingValue="true")
public class RecruitmentKillSwitchReconciler {
    private final RecruitmentInterviewRepository interviews;
    private final RecruitmentCallCancellationCoordinator cancellations;

    @Scheduled(fixedDelayString="${app.recruitment.kill-switch-reconcile-ms:5000}")
    @Transactional
    public void terminateDisabledCalls() {
        for(var interview:interviews.lockKillSwitchedCalls()) {
            cancellations.cancel(interview,"TENANT_CALLING_KILL_SWITCH");
            interviews.save(interview);
        }
    }
}
