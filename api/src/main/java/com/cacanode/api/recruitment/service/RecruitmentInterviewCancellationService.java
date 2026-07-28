package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.query.RecruitmentCvStorageService;
import com.cacanode.api.recruitment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class RecruitmentInterviewCancellationService {
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentInterviewRepository interviews;
    private final RecruitmentInterviewInvitationTokenRepository invitationTokens;
    private final RecruitmentCandidateEmailDeliveryRepository deliveries;
    private final RecruitmentCandidateSessionRepository sessions;
    private final RecruitmentApplicationEmailTokenRepository applicationTokens;
    private final HiringQuotaApi quota;
    private final Clock clock;
    @Autowired(required=false) private RecruitmentCvStorageService cvs;
    @Autowired(required=false) private RecruitmentCvAnalysisService cvAnalysis;
    @Autowired(required=false) private RecruitmentCallCancellationCoordinator callCancellation;
    @Autowired(required=false) private RecruitmentProjectionEventPublisher projectionEvents;

    @Transactional
    public RecruitmentApplication withdraw(UUID tenantId,UUID applicationId){
        RecruitmentApplication application=applications.findForUpdate(tenantId,applicationId).orElseThrow();
        LocalDateTime now=LocalDateTime.now(clock);
        if(application.getStatus()!=ApplicationStatus.WITHDRAWN){application.setStatus(ApplicationStatus.WITHDRAWN);application.setWithdrawnAt(now);application=applications.saveAndFlush(application);if(projectionEvents!=null)projectionEvents.application(application,"application.withdrawn");}
        interviews.findByApplicationForUpdate(tenantId,applicationId).ifPresent(i->cancel(i,now,false));
        sessions.revokeApplication(applicationId,now);applicationTokens.revokeActive(applicationId,now);
        if(cvAnalysis!=null)cvAnalysis.cancel(tenantId,applicationId);
        if(cvs!=null){cvs.scheduleImmediateDeletion(tenantId,applicationId);cvs.deleteNow(tenantId,applicationId);}return application;
    }

    @Transactional
    public void closeJob(UUID tenantId,UUID jobId){LocalDateTime now=LocalDateTime.now(clock);for(var interview:interviews.findJobForUpdate(tenantId,jobId))cancel(interview,now,interview.getStatus()==InterviewStatus.INVITED);}

    @Scheduled(fixedDelayString="${app.recruitment.invitation-expiry-ms:60000}")
    @Transactional
    public void expireInvitations(){LocalDateTime now=LocalDateTime.now(clock);for(var interview:interviews.lockExpiredInvitations(now))cancel(interview,now,true);}

    private void cancel(RecruitmentInterview interview,LocalDateTime now,boolean expire){
        if(java.util.Set.of(InterviewStatus.COMPLETED,InterviewStatus.NO_ANSWER,InterviewStatus.DECLINED,
                InterviewStatus.FAILED,InterviewStatus.CANCELLED,InterviewStatus.EXPIRED).contains(interview.getStatus()))return;
        boolean answered=callCancellation!=null&&callCancellation.cancel(interview,expire?"INTERVIEW_EXPIRED":"INTERVIEW_CANCELLED");
        if(!answered&&interview.getQuotaReservationId()!=null){try{quota.releaseInterviewSeconds(interview.getTenantId(),interview.getQuotaReservationId());}catch(HiringQuotaApi.HiringQuotaException ignored){}}
        interview.setStatus(expire?InterviewStatus.EXPIRED:InterviewStatus.CANCELLED);
        if(expire)interview.setExpiredAt(now);else interview.setCancelledAt(now);
        interview=interviews.save(interview);if(projectionEvents!=null)projectionEvents.interview(interview,expire?"interview.expired":"interview.cancelled");invitationTokens.revokeInterview(interview.getId(),now);
        deliveries.cancelInterview(interview.getId(),CandidateEmailState.CANCELLED,now);
    }
}
