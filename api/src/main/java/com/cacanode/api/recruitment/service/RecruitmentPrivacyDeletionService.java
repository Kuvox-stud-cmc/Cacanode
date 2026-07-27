package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.event.AuditLogEvent;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.recruitment.api.event.CandidatePrivacyDeletionConfirmationRequestedEvent;
import com.cacanode.api.recruitment.api.event.RecruitmentErasureCompletedEvent;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.dto.RecruitmentPrivacyDtos;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.query.CandidateAccessService;
import com.cacanode.api.recruitment.query.RecruitmentCvStorageService;
import com.cacanode.api.recruitment.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class RecruitmentPrivacyDeletionService {
    private final RecruitmentPrivacyDeletionRequestRepository requests;
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentCandidateRepository candidates;
    private final RecruitmentJobRepository jobs;
    private final RecruitmentInterviewRepository interviews;
    private final RecruitmentApplicationEmailTokenRepository emailTokens;
    private final RecruitmentCandidateSessionRepository sessions;
    private final RecruitmentPrivacyErasureRepository erasure;
    private final CandidateAccessService candidateAccess;
    private final RecruitmentCvStorageService cvs;
    private final RecruitmentInterviewCancellationService cancellations;
    private final RecruitmentTokenSupport tokens;
    private final PublicRecruitmentProperties properties;
    private final ApplicationEventPublisher events;
    private final MeterRegistry metrics;
    private final Clock clock;

    @Transactional
    public RecruitmentPrivacyDtos.Status requestByAdmin(UUID tenantId,UUID applicationId,UUID actorId,String verificationReference) {
        RecruitmentApplication application=applications.findForUpdate(tenantId,applicationId)
                .orElseThrow(()->new ResourceNotFoundException("Application was not found"));
        RecruitmentPrivacyDeletionRequest request=create(application,PrivacyDeletionRequesterKind.TENANT_ADMIN,
                PrivacyDeletionStatus.PENDING,verificationReference);request.setConfirmedAt(clock.instant());
        request.setNextAttemptAt(clock.instant());request=requests.saveAndFlush(request);
        audit(request,actorId);return response(request);
    }

    @Transactional
    public RecruitmentPrivacyDtos.Status requestByCandidate(String accessToken,String csrfToken) {
        CandidateAccessService.DeletionSubject subject=candidateAccess.authorizeDeletion(accessToken,csrfToken);
        RecruitmentApplication application=applications.findForUpdate(subject.tenantId(),subject.applicationId())
                .orElseThrow(RecruitmentPrivacyDeletionService::unauthorized);
        RecruitmentPrivacyDeletionRequest request=create(application,PrivacyDeletionRequesterKind.CANDIDATE,
                PrivacyDeletionStatus.PENDING_CONFIRMATION,null);request=requests.saveAndFlush(request);
        String raw=tokens.opaqueToken();RecruitmentApplicationEmailToken token=new RecruitmentApplicationEmailToken();
        token.setTenantId(application.getTenantId());token.setApplicationId(application.getId());token.setJobId(application.getJobId());
        token.setPurpose(EmailTokenPurpose.DELETION_CONFIRMATION);token.setTokenHash(tokens.hash(raw));
        token.setExpiresAt(LocalDateTime.now(clock).plusHours(1));emailTokens.saveAndFlush(token);
        RecruitmentCandidate candidate=candidates.findByIdAndTenantId(application.getCandidateId(),application.getTenantId()).orElseThrow();
        RecruitmentJob job=jobs.findByIdAndTenantId(application.getJobId(),application.getTenantId()).orElseThrow();
        events.publishEvent(new CandidatePrivacyDeletionConfirmationRequestedEvent(candidate.getEmail(),candidate.getFullName(),
                job.getFrozenCompanyName(),job.getTitle(),application.getLocale(),RecruitmentCandidateLinks.withToken(properties.candidateBaseUrl(),"deletion",raw)));
        audit(request,null);return response(request);
    }

    @Transactional
    public RecruitmentPrivacyDtos.Status confirm(String rawToken) {
        RecruitmentApplicationEmailToken token=emailTokens.findForUpdateByHash(tokens.hash(rawToken))
                .orElseThrow(RecruitmentPrivacyDeletionService::unauthorized);
        LocalDateTime now=LocalDateTime.now(clock);
        if(token.getPurpose()!=EmailTokenPurpose.DELETION_CONFIRMATION||token.getConsumedAt()!=null
                ||token.getRevokedAt()!=null||!token.getExpiresAt().isAfter(now))throw unauthorized();
        RecruitmentPrivacyDeletionRequest request=requests.findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(
                token.getApplicationId(),PrivacyDeletionStatus.PENDING_CONFIRMATION).orElseThrow(RecruitmentPrivacyDeletionService::unauthorized);
        token.setConsumedAt(now);emailTokens.save(token);request.setStatus(PrivacyDeletionStatus.PENDING);
        request.setConfirmedAt(clock.instant());request.setNextAttemptAt(clock.instant());return response(requests.save(request));
    }

    @Transactional(readOnly=true)
    public List<RecruitmentPrivacyDtos.Status> list(UUID tenantId,int page,int size) {
        if(page<0||size<1||size>100)throw new ConflictException("Invalid deletion request page");
        return requests.findByTenantIdOrderByCreatedAtDesc(tenantId,PageRequest.of(page,size)).stream().map(this::response).toList();
    }

    @Scheduled(fixedDelayString="${app.recruitment.privacy-deletion-worker-ms:30000}")
    @Transactional
    public void processDue() {for(var request:requests.lockDue(clock.instant()))process(request);}

    void process(RecruitmentPrivacyDeletionRequest request) {
        request.setStatus(PrivacyDeletionStatus.PROCESSING);request.setAttempts(request.getAttempts()+1);
        requests.save(request);
        try {
            RecruitmentApplication application=applications.findForUpdate(request.getTenantId(),request.getApplicationId()).orElse(null);
            if(application!=null) {
                sessions.revokeApplication(application.getId(),LocalDateTime.now(clock));
                emailTokens.revokeActive(application.getId(),LocalDateTime.now(clock));
                erasure.cancelCandidateEmailDeliveries(request.getTenantId(),application.getId());
                if(application.getStatus()!=ApplicationStatus.WITHDRAWN)cancellations.withdraw(request.getTenantId(),application.getId());
                cvs.scheduleImmediateDeletion(request.getTenantId(),application.getId());cvs.deleteNow(request.getTenantId(),application.getId());
                erasure.requestRecordingDeletion(request.getTenantId(),application.getId());
                if(erasure.pendingRecordingDeletionCount(request.getTenantId(),application.getId())>0)
                    throw new IllegalStateException("EXTERNAL_DELETION_PENDING");
                List<UUID> interviewIds=interviews.findByTenantIdAndApplicationId(request.getTenantId(),application.getId())
                        .map(value->List.of(value.getId())).orElse(List.of());
                UUID candidateId=application.getCandidateId();applications.delete(application);applications.flush();
                if(!applications.existsByTenantIdAndCandidateId(request.getTenantId(),candidateId))
                    candidates.findByIdAndTenantId(candidateId,request.getTenantId()).ifPresent(candidates::delete);
                events.publishEvent(new RecruitmentErasureCompletedEvent(request.getTenantId(),request.getApplicationId(),
                        interviewIds,"COMPLETED",clock.instant()));
            }
            request.setStatus(PrivacyDeletionStatus.COMPLETED);request.setCompletedAt(clock.instant());
            request.setNextAttemptAt(null);request.setLastErrorCode(null);metrics.counter("recruitment.privacy.erasure","outcome","completed").increment();
        } catch(RuntimeException exception) {
            String code=exception.getMessage()==null?exception.getClass().getSimpleName():exception.getMessage();
            request.setLastErrorCode(code.substring(0,Math.min(100,code.length())));
            if(request.getAttempts()>=10){request.setStatus(PrivacyDeletionStatus.EXHAUSTED);request.setExhaustedAt(clock.instant());request.setNextAttemptAt(null);}
            else {request.setStatus(PrivacyDeletionStatus.RETRY);request.setNextAttemptAt(clock.instant().plusSeconds(Math.min(3600,5L<<Math.min(request.getAttempts(),9))));}
            metrics.counter("recruitment.privacy.erasure","outcome",request.getStatus().name().toLowerCase()).increment();
        }
        requests.save(request);
    }

    private RecruitmentPrivacyDeletionRequest create(RecruitmentApplication application,PrivacyDeletionRequesterKind kind,
            PrivacyDeletionStatus status,String verification) {
        RecruitmentPrivacyDeletionRequest value=new RecruitmentPrivacyDeletionRequest();value.setTenantId(application.getTenantId());
        value.setApplicationId(application.getId());value.setCandidateId(application.getCandidateId());value.setRequesterKind(kind);
        value.setStatus(status);value.setVerificationReference(verification);return value;
    }
    private void audit(RecruitmentPrivacyDeletionRequest request,UUID actorId){events.publishEvent(AuditLogEvent.builder(this)
            .tenantId(request.getTenantId()).userId(actorId).action(LogAction.RECRUITMENT_PRIVACY_DELETION_REQUESTED)
            .resourceType("recruitment_privacy_deletion_request").resourceId(request.getId())
            .metadata(java.util.Map.of("requesterKind",request.getRequesterKind().name())).build());}
    private RecruitmentPrivacyDtos.Status response(RecruitmentPrivacyDeletionRequest value){return new RecruitmentPrivacyDtos.Status(
            value.getId(),value.getApplicationId(),value.getCandidateId(),value.getRequesterKind(),value.getStatus(),
            value.getAttempts(),value.getLastErrorCode(),value.getConfirmedAt(),value.getCompletedAt(),value.getExhaustedAt(),value.getCreatedAt());}
    private static UnauthorizedException unauthorized(){return new UnauthorizedException("Invalid or expired deletion confirmation");}
}
