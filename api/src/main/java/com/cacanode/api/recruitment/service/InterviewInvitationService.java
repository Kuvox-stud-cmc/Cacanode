package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class InterviewInvitationService {
    private static final Set<ApplicationStatus> ELIGIBLE=Set.of(ApplicationStatus.SUBMITTED,ApplicationStatus.INTERVIEW_INVITED);
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentInterviewRepository interviews;
    private final RecruitmentJobRepository jobs;
    private final RecruitmentTenantSettingsRepository settings;
    private final RecruitmentAvailabilityWindowRepository availabilityWindows;
    private final RecruitmentAvailabilityExceptionRepository availabilityExceptions;
    private final RecruitmentCandidateEmailDeliveryRepository deliveries;
    private final Clock clock;
    private final RecruitmentProperties properties;
    @Autowired(required=false) private RecruitmentProjectionEventPublisher projectionEvents;
    @Autowired(required=false) private RecruitmentCapabilityService capabilities;

    @Transactional
    public RecruitmentInterview invite(UUID tenantId,UUID applicationId,boolean manual) {
        if(capabilities!=null)capabilities.requireMasterEnabled(tenantId);
        if(!properties.publicJobsEnabled())throw new ConflictException("Public interview scheduling is disabled");
        RecruitmentTenantSettings tenantSettings=settings.findById(tenantId).orElse(null);
        ZoneId schedulingZone=ZoneId.of(tenantSettings==null?"Asia/Ho_Chi_Minh":tenantSettings.getSchedulingTimezone());
        LocalDate today=Instant.now(clock).atZone(schedulingZone).toLocalDate();
        if(!availabilityWindows.existsByTenantId(tenantId)
                && !availabilityExceptions.existsByTenantIdAndKindAndExceptionDateGreaterThanEqual(
                        tenantId,AvailabilityExceptionKind.EXTRA,today))
            throw new ConflictException("INTERVIEW_AVAILABILITY_NOT_CONFIGURED");
        RecruitmentApplication application=applications.findForUpdate(tenantId,applicationId)
                .orElseThrow(()->new ResourceNotFoundException("Application was not found"));
        RecruitmentJob job=jobs.findByIdAndTenantId(application.getJobId(),tenantId)
                .orElseThrow(()->new ResourceNotFoundException("Job was not found"));
        if (!ELIGIBLE.contains(application.getStatus()) || job.getStatus()==JobStatus.CLOSED || job.getStatus()==JobStatus.ARCHIVED)
            throw new ConflictException("Application cannot be invited");
        RecruitmentInterview interview=interviews.findByApplicationForUpdate(tenantId,applicationId).orElse(null);
        LocalDateTime now=LocalDateTime.now(clock);
        int lifetime=tenantSettings==null?7:tenantSettings.getInvitationLifetimeDays();
        if(interview==null){
            interview=new RecruitmentInterview();interview.setTenantId(tenantId);interview.setApplicationId(applicationId);
            interview.setJobId(application.getJobId());interview.setStatus(InterviewStatus.INVITED);
            interview.setTemplateRevisionId(application.getTemplateRevisionId());interview.setTemplateSnapshot(application.getTemplateSnapshot());
            interview.setTemplateSnapshotSha256(application.getTemplateSnapshotSha256());interview.setTemplateSnapshotVersion(application.getTemplateSnapshotVersion());
            interview.setRecordingEnabled(job.isRecordingEnabled());interview.setRecordingRetentionDays(job.getRecordingRetentionDays());
        }else if(interview.getStatus()==InterviewStatus.SCHEDULED){throw new ConflictException("Interview is already scheduled");}
        else if(interview.getStatus()!=InterviewStatus.INVITED && interview.getStatus()!=InterviewStatus.EXPIRED){
            throw new ConflictException("Interview invitation is terminal");
        }
        if(interview.getStatus()==InterviewStatus.EXPIRED || interview.getInvitationExpiresAt()==null || !interview.getInvitationExpiresAt().isAfter(now)){
            interview.setStatus(InterviewStatus.INVITED);interview.setExpiredAt(null);interview.setInvitedAt(now);
            interview.setInvitationExpiresAt(now.plusDays(lifetime));
        }else if(interview.getInvitedAt()==null){interview.setInvitedAt(now);interview.setInvitationExpiresAt(now.plusDays(lifetime));}
        interview=interviews.saveAndFlush(interview);
        application.setStatus(ApplicationStatus.INTERVIEW_INVITED);application=applications.save(application);
        if(projectionEvents!=null){projectionEvents.interview(interview,"interview.invited");projectionEvents.application(application,null);}
        String key=manual?"invitation-manual-"+UUID.randomUUID():"invitation-initial";
        if(manual){
            var recent=deliveries.findFirstByTenantIdAndApplicationIdAndKindOrderByCreatedAtDesc(
                    tenantId,applicationId,CandidateEmailKind.INVITATION).orElse(null);
            if(recent!=null && recent.getCreatedAt()!=null && recent.getCreatedAt().plusSeconds(60).isAfter(now))
                throw new ConflictException("Invitation was sent less than 60 seconds ago");
        }
        enqueue(interview,CandidateEmailKind.INVITATION,key,now);
        return interview;
    }

    public void enqueue(RecruitmentInterview interview,CandidateEmailKind kind,String key,LocalDateTime due) {
        String dedupeKey=interview.getId()+":"+key;
        if(deliveries.findByTenantIdAndDedupeKey(interview.getTenantId(),dedupeKey).isPresent())return;
        RecruitmentCandidateEmailDelivery delivery=new RecruitmentCandidateEmailDelivery();
        delivery.setTenantId(interview.getTenantId());delivery.setInterviewId(interview.getId());
        delivery.setApplicationId(interview.getApplicationId());delivery.setKind(kind);delivery.setDedupeKey(dedupeKey);
        delivery.setDueAt(due);delivery.setNextAttemptAt(due);
        deliveries.saveAndFlush(delivery);
    }
}
