package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.model.RecruitmentApplication;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import com.cacanode.api.recruitment.repository.RecruitmentJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false} and ${app.recruitment.automation-enabled:false}")
public class RecruitmentAutomationService {
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentJobRepository jobs;
    private final InterviewInvitationService invitations;
    private final ScreeningSupport screening;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private RecruitmentCapabilityService capabilities;

    @Transactional
    public void process(UUID tenantId,UUID applicationId) {
        if(capabilities!=null&&!capabilities.capabilities(tenantId).automationEnabled())return;
        RecruitmentApplication application=applications.findForUpdate(tenantId,applicationId).orElse(null);
        if(application==null || application.getAutomationOutcome()!=AutomationOutcome.PENDING
                || application.getStatus()!=ApplicationStatus.SUBMITTED)return;
        var job=jobs.findByIdAndTenantId(application.getJobId(),tenantId).orElse(null);
        if(job==null || job.getStatus()!=JobStatus.PUBLISHED)return;
        AutomationMode mode=application.getAutomationModeSnapshot();
        if(mode==AutomationMode.MANUAL){finish(application,AutomationOutcome.MANUAL);return;}
        if(mode==AutomationMode.AUTO_INVITE_MATCHING && !screening.matches(
                application.getScreeningConfigSnapshot(),application.getScreeningAnswers())){
            finish(application,AutomationOutcome.NOT_MATCHED);return;
        }
        invitations.invite(tenantId,applicationId,false);
        finish(application,AutomationOutcome.INVITED);
    }

    @Scheduled(fixedDelayString="${app.recruitment.automation-reconcile-ms:60000}")
    @Transactional
    public void reconcile(){for(var application:applications.lockPendingAutomation())process(application.getTenantId(),application.getId());}

    private void finish(RecruitmentApplication application,AutomationOutcome outcome){
        application.setAutomationOutcome(outcome);application.setAutomationEvaluatedAt(LocalDateTime.now(clock));applications.save(application);
    }
}
