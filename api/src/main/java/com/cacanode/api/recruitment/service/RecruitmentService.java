package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.BillingModuleApi;
import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.recruitment.api.RecruitmentApplicationCommandApi;
import com.cacanode.api.recruitment.api.RecruitmentInterviewCommandApi;
import com.cacanode.api.recruitment.api.event.PublicJobProjectionChangedEvent;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.query.RecruitmentQueryService;
import com.cacanode.api.recruitment.query.RecruitmentCvStorageService;
import com.cacanode.api.recruitment.repository.*;
import com.cacanode.api.tenant.api.TenantPublicProfileApi;
import com.cacanode.api.tenant.api.TenantStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.recruitment", name = "enabled", havingValue = "true")
public class RecruitmentService implements RecruitmentApplicationCommandApi, RecruitmentInterviewCommandApi {
    private final RecruitmentTenantSettingsRepository settingsRepository;
    private final RecruitmentJobRepository jobRepository;
    private final InterviewTemplateRepository templateRepository;
    private final InterviewTemplateRevisionRepository revisionRepository;
    private final RecruitmentCandidateRepository candidateRepository;
    private final RecruitmentApplicationRepository applicationRepository;
    private final RecruitmentInterviewRepository interviewRepository;
    private final HiringQuotaApi hiringQuotaApi;
    private final BillingModuleApi billingModuleApi;
    private final TenantPublicProfileApi tenantPublicProfileApi;
    private final RecruitmentQueryService queries;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final ScreeningSupport screening;
    private final ApplicationSubmissionTransitionService submissionTransition;
    private final RecruitmentInterviewCancellationService cancellations;
    private final RecruitmentCapabilityService capabilities;
    @Autowired(required=false) private RecruitmentCvStorageService cvStorageService;
    @Autowired(required=false) private RecruitmentProjectionEventPublisher projectionEvents;
    @Value("${app.recruitment.recording-enabled:false}") private boolean globalRecordingEnabled;

    @Transactional(readOnly = true)
    public RecruitmentDtos.SettingsResponse settings(UUID tenantId) {
        return settingsRepository.findById(tenantId).map(this::settingsResponse)
                .orElse(new RecruitmentDtos.SettingsResponse(AutomationMode.MANUAL, CvAiMode.OFF,
                        null, false, 0, "Asia/Ho_Chi_Minh",15,120,30,7,120,
                        java.util.List.of(1440,60),0));
    }

    @Transactional
    public RecruitmentDtos.SettingsResponse updateSettings(UUID tenantId, RecruitmentDtos.SettingsUpdate request) {
        capabilities.requireMasterEnabled(tenantId);
        if (request.defaultTemplateRevisionId() != null) {
            validateRevision(tenantId, request.defaultTemplateRevisionId(), true);
        }
        validateRecording(tenantId, request.recordingEnabled(), request.recordingRetentionDays());
        try { ZoneId.of(request.schedulingTimezone()); }
        catch (RuntimeException e) { throw new BadRequestException("Scheduling timezone must be a valid IANA timezone"); }
        RecruitmentTenantSettings settings = settingsRepository.findForUpdate(tenantId).orElseGet(() -> {
            RecruitmentTenantSettings value = new RecruitmentTenantSettings(); value.setTenantId(tenantId); return value;
        });
        settings.setDefaultAutomationMode(request.defaultAutomationMode());
        settings.setCvAiMode(request.cvAiMode());
        settings.setDefaultTemplateRevisionId(request.defaultTemplateRevisionId());
        settings.setRecordingEnabled(request.recordingEnabled());
        settings.setRecordingRetentionDays(request.recordingRetentionDays());
        settings.setSchedulingTimezone(request.schedulingTimezone());
        settings.setSlotGridMinutes(15);
        settings.setMinimumNoticeMinutes(request.minimumNoticeMinutes());
        settings.setBookingHorizonDays(request.bookingHorizonDays());
        settings.setInvitationLifetimeDays(request.invitationLifetimeDays());
        settings.setRescheduleCutoffMinutes(request.rescheduleCutoffMinutes());
        settings.setReminderOffsetsMinutes(request.reminderOffsetsMinutes()==null?java.util.List.of():request.reminderOffsetsMinutes());
        return settingsResponse(settingsRepository.save(settings));
    }

    @Transactional
    public RecruitmentDtos.JobResponse createJob(UUID tenantId, RecruitmentDtos.JobWrite request) {
        capabilities.requireMasterEnabled(tenantId);
        validateJobWrite(tenantId, request, false);
        RecruitmentJob job = new RecruitmentJob(); job.setTenantId(tenantId); job.setPublicId(UUID.randomUUID());
        job.setStatus(JobStatus.DRAFT); apply(job, request); job=jobRepository.save(job); emit(job,null); return jobResponse(job);
    }

    @Transactional(readOnly = true)
    public RecruitmentDtos.JobResponse job(UUID tenantId, UUID id) { return jobResponse(requireJob(tenantId,id)); }

    @Transactional
    public RecruitmentDtos.JobResponse updateJob(UUID tenantId, UUID id, RecruitmentDtos.JobWrite request) {
        capabilities.requireMasterEnabled(tenantId);
        RecruitmentJob job = lockJob(tenantId,id);
        if (job.getStatus()!=JobStatus.DRAFT && job.getStatus()!=JobStatus.PAUSED) throw conflict("Job content can be edited only in DRAFT or PAUSED");
        validateJobWrite(tenantId,request,job.getStatus()!=JobStatus.DRAFT); apply(job,request);
        job=jobRepository.save(job); emit(job,null); return jobResponse(job);
    }

    @Transactional
    public void deleteJob(UUID tenantId,UUID id){RecruitmentJob job=lockJob(tenantId,id);if(job.getStatus()!=JobStatus.DRAFT||applicationRepository.existsByTenantIdAndJobId(tenantId,id))throw conflict("Only an unreferenced DRAFT job can be deleted");jobRepository.delete(job);}

    @Transactional
    public RecruitmentDtos.JobResponse publish(UUID tenantId,UUID id){
        capabilities.requireMasterEnabled(tenantId);
        RecruitmentJob job=lockJob(tenantId,id);
        if(job.getStatus()==JobStatus.PUBLISHED){publishProjection(job,true);return jobResponse(job);}
        if(job.getStatus()==JobStatus.PAUSED){if(job.getClosingAt()==null||!job.getClosingAt().isAfter(now()))throw conflict("Published jobs require a future closing date");validateRevision(tenantId,job.getTemplateRevisionId(),true);resolveEffectiveSettings(job,settings(tenantId));if(job.getEffectiveAutomationMode()==AutomationMode.AUTO_INVITE_MATCHING)screening.requireMatchingCriteria(job.getScreeningConfig());job.setStatus(JobStatus.PUBLISHED);job.setPausedAt(null);job=jobRepository.save(job);publishProjection(job,true);emit(job,"job.published");return jobResponse(job);}
        if(job.getStatus()!=JobStatus.DRAFT)throw conflict("Only DRAFT or PAUSED jobs can be published");
        LocalDateTime now=now(); if(job.getClosingAt()==null||!job.getClosingAt().isAfter(now))throw conflict("Published jobs require a future closing date");
        RecruitmentDtos.SettingsResponse settings=settings(tenantId);
        UUID revisionId=job.getTemplateRevisionId()!=null?job.getTemplateRevisionId():settings.defaultTemplateRevisionId();
        InterviewTemplateRevision revision=validateRevision(tenantId,revisionId,true);
        TenantPublicProfileApi.TenantPublicProfile profile=tenantPublicProfileApi.getPublicProfile(tenantId);
        if(profile.status()!=TenantStatus.ACTIVE&&profile.status()!=TenantStatus.TRIAL)throw conflict("Tenant profile is not active");
        try {
            HiringQuotaApi.Reservation reservation=hiringQuotaApi.reserveActiveJob(tenantId,job.getId());
            job.setTemplateRevisionId(revision.getId()); job.setActiveJobReservationId(reservation.reservationId());
        } catch(HiringQuotaApi.HiringQuotaException exception){throw conflict(exception.getMessage());}
        job.setFrozenCompanyName(profile.companyName());job.setFrozenCompanySlug(profile.slug());job.setPublishedAt(now);job.setPausedAt(null);job.setStatus(JobStatus.PUBLISHED);
        resolveEffectiveSettings(job,settings);
        if(job.getEffectiveAutomationMode()==AutomationMode.AUTO_INVITE_MATCHING)screening.requireMatchingCriteria(job.getScreeningConfig());
        job=jobRepository.save(job);publishProjection(job,true);emit(job,"job.published");return jobResponse(job);
    }

    @Transactional
    public RecruitmentDtos.JobResponse pause(UUID tenantId,UUID id){RecruitmentJob job=lockJob(tenantId,id);if(job.getStatus()==JobStatus.PAUSED){publishProjection(job,false);return jobResponse(job);}if(job.getStatus()!=JobStatus.PUBLISHED)throw conflict("Only a PUBLISHED job can be paused");job.setStatus(JobStatus.PAUSED);job.setPausedAt(now());job=jobRepository.save(job);publishProjection(job,false);emit(job,"job.paused");return jobResponse(job);}

    @Transactional
    public RecruitmentDtos.JobResponse close(UUID tenantId,UUID id){RecruitmentJob job=lockJob(tenantId,id);if(job.getStatus()==JobStatus.CLOSED||job.getStatus()==JobStatus.ARCHIVED){publishProjection(job,false);return jobResponse(job);}if(job.getStatus()!=JobStatus.PUBLISHED&&job.getStatus()!=JobStatus.PAUSED)throw conflict("Only a PUBLISHED or PAUSED job can be closed");cancellations.closeJob(tenantId,id);try{hiringQuotaApi.releaseActiveJob(tenantId,id,job.getActiveJobReservationId());}catch(HiringQuotaApi.HiringQuotaException e){throw conflict(e.getMessage());}job.setStatus(JobStatus.CLOSED);job.setClosedAt(now());job=jobRepository.save(job);publishProjection(job,false);emit(job,"job.closed");return jobResponse(job);}

    @Transactional
    public RecruitmentDtos.JobResponse archive(UUID tenantId,UUID id){RecruitmentJob job=lockJob(tenantId,id);if(job.getStatus()==JobStatus.ARCHIVED){publishProjection(job,false);return jobResponse(job);}if(job.getStatus()!=JobStatus.CLOSED)throw conflict("Only a CLOSED job can be archived");job.setStatus(JobStatus.ARCHIVED);job.setArchivedAt(now());job=jobRepository.save(job);publishProjection(job,false);emit(job,"job.archived");return jobResponse(job);}

    @Transactional
    public RecruitmentDtos.TemplateResponse createTemplate(UUID tenantId,RecruitmentDtos.TemplateCreate request){
        capabilities.requireMasterEnabled(tenantId);
        requireLocale(request.locale()); TemplateSnapshotSupport snapshots=new TemplateSnapshotSupport(objectMapper);var snapshot=snapshots.validateAndCreate(request.locale(),request.content());
        InterviewTemplate template=new InterviewTemplate();template.setTenantId(tenantId);template.setName(request.name().strip());template.setDescription(trim(request.description()));template.setLocale(request.locale());
        template=templateRepository.saveAndFlush(template); createRevision(template,1,snapshot); return templateResponse(template,1);
    }

    @Transactional(readOnly=true)
    public RecruitmentDtos.TemplateResponse template(UUID tenantId,UUID id){InterviewTemplate t=requireTemplate(tenantId,id);int latest=revisionRepository.findFirstByTenantIdAndTemplateIdOrderByRevisionNumberDesc(tenantId,id).map(InterviewTemplateRevision::getRevisionNumber).orElse(0);return templateResponse(t,latest);}

    @Transactional
    public RecruitmentDtos.TemplateResponse patchTemplate(UUID tenantId,UUID id,RecruitmentDtos.TemplatePatch request){InterviewTemplate t=templateRepository.findForUpdate(tenantId,id).orElseThrow(()->notFound("Template"));if(request.name()!=null){if(request.name().isBlank())throw new BadRequestException("Template name cannot be blank");t.setName(request.name().strip());}if(request.description()!=null)t.setDescription(trim(request.description()));templateRepository.save(t);return template(tenantId,id);}

    @Transactional
    public void archiveTemplate(UUID tenantId,UUID id){InterviewTemplate t=templateRepository.findForUpdate(tenantId,id).orElseThrow(()->notFound("Template"));if(!t.isArchived()){t.setArchived(true);t.setArchivedAt(now());templateRepository.save(t);}}

    @Transactional
    public RecruitmentDtos.RevisionResponse addRevision(UUID tenantId,UUID templateId,RecruitmentDtos.RevisionCreate request){InterviewTemplate t=templateRepository.findForUpdate(tenantId,templateId).orElseThrow(()->notFound("Template"));if(t.isArchived())throw conflict("Archived templates cannot be revised");var snapshot=new TemplateSnapshotSupport(objectMapper).validateAndCreate(t.getLocale(),request.content());int next=revisionRepository.findFirstByTenantIdAndTemplateIdOrderByRevisionNumberDesc(tenantId,templateId).map(r->r.getRevisionNumber()+1).orElse(1);return revisionResponse(createRevision(t,next,snapshot));}

    @Transactional(readOnly=true)
    public java.util.List<RecruitmentDtos.RevisionResponse> revisions(UUID tenantId,UUID templateId){requireTemplate(tenantId,templateId);return revisionRepository.findByTenantIdAndTemplateIdOrderByRevisionNumberDesc(tenantId,templateId).stream().map(this::revisionResponse).toList();}

    @Transactional(readOnly=true)
    public RecruitmentDtos.RevisionResponse revision(UUID tenantId,UUID templateId,UUID revisionId){InterviewTemplateRevision r=revisionRepository.findByIdAndTenantId(revisionId,tenantId).filter(x->x.getTemplateId().equals(templateId)).orElseThrow(()->notFound("Template revision"));return revisionResponse(r);}

    @Transactional
    public RecruitmentDtos.CandidateResponse createCandidate(UUID tenantId,RecruitmentDtos.CandidateWrite request){capabilities.requireMasterEnabled(tenantId);RecruitmentCandidate c=new RecruitmentCandidate();c.setTenantId(tenantId);apply(c,request);try{return candidateResponse(candidateRepository.saveAndFlush(c));}catch(DataIntegrityViolationException e){throw conflict("A candidate with this email already exists");}}
    @Transactional(readOnly=true) public RecruitmentDtos.CandidateResponse candidate(UUID tenantId,UUID id){return candidateResponse(requireCandidate(tenantId,id));}
    @Transactional public RecruitmentDtos.CandidateResponse updateCandidate(UUID tenantId,UUID id,RecruitmentDtos.CandidateWrite request){RecruitmentCandidate c=candidateRepository.findForUpdate(tenantId,id).orElseThrow(()->notFound("Candidate"));apply(c,request);try{return candidateResponse(candidateRepository.saveAndFlush(c));}catch(DataIntegrityViolationException e){throw conflict("A candidate with this email already exists");}}
    @Transactional public void deleteCandidate(UUID tenantId,UUID id){RecruitmentCandidate c=candidateRepository.findForUpdate(tenantId,id).orElseThrow(()->notFound("Candidate"));if(applicationRepository.existsByTenantIdAndCandidateId(tenantId,id))throw conflict("Candidate is referenced by an application");candidateRepository.delete(c);}

    @Override @Transactional
    public CreatedApplication create(CreateApplicationCommand command){capabilities.requireMasterEnabled(command.tenantId());RecruitmentJob job=lockJob(command.tenantId(),command.jobId());if(job.getStatus()!=JobStatus.PUBLISHED&&job.getStatus()!=JobStatus.PAUSED)throw conflict("Applications require a published job");requireCandidate(command.tenantId(),command.candidateId());InterviewTemplateRevision revision=validateRevision(command.tenantId(),job.getTemplateRevisionId(),false);RecruitmentApplication a=new RecruitmentApplication();a.setTenantId(command.tenantId());a.setJobId(job.getId());a.setCandidateId(command.candidateId());a.setStatus(ApplicationStatus.SUBMITTED_UNVERIFIED);LocalDateTime submitted=now();a.setSubmittedAt(submitted);a.setLocale(job.getLanguage());a.setPrivacyConsentAt(submitted);a.setCvPresent(command.cvPresent());a.setCvAnalysisStatus(CvAnalysisStatus.NOT_REQUESTED);a.setCvAiModeSnapshot(job.getEffectiveCvAiMode()==null?CvAiMode.OFF:job.getEffectiveCvAiMode());a.setTemplateRevisionId(revision.getId());a.setTemplateSnapshot(revision.getContent());a.setTemplateSnapshotSha256(revision.getContentSha256());a.setTemplateSnapshotVersion(Integer.toString(revision.getRevisionNumber()));a.setScreeningConfigSnapshot(job.getScreeningConfig());a.setScreeningAnswers("[]");a.setAutomationModeSnapshot(job.getEffectiveAutomationMode()==null?AutomationMode.MANUAL:job.getEffectiveAutomationMode());try{a=applicationRepository.saveAndFlush(a);}catch(DataIntegrityViolationException e){throw conflict("Candidate has already applied to this job");}emit(a,null);return new CreatedApplication(a.getId(),a.getStatus().name(),a.getTemplateSnapshotSha256());}

    @Override @Transactional
    public RecruitmentInterviewCommandApi.CreatedInterview create(RecruitmentInterviewCommandApi.CreateInterviewCommand command){
        RecruitmentApplication application=applicationRepository.findForUpdate(command.tenantId(),command.applicationId()).orElseThrow(()->notFound("Application"));
        RecruitmentInterview existing=interviewRepository.findByTenantIdAndApplicationId(command.tenantId(),command.applicationId()).orElse(null);
        if(existing!=null)return new RecruitmentInterviewCommandApi.CreatedInterview(existing.getId(),existing.getStatus().name(),existing.getTemplateSnapshotSha256());
        RecruitmentJob job=requireJob(command.tenantId(),application.getJobId());
        RecruitmentInterview interview=new RecruitmentInterview();interview.setTenantId(command.tenantId());interview.setApplicationId(application.getId());interview.setJobId(application.getJobId());interview.setStatus(command.scheduledAt()==null?InterviewStatus.INVITED:InterviewStatus.SCHEDULED);interview.setTemplateRevisionId(application.getTemplateRevisionId());interview.setTemplateSnapshot(application.getTemplateSnapshot());interview.setTemplateSnapshotSha256(application.getTemplateSnapshotSha256());interview.setTemplateSnapshotVersion(application.getTemplateSnapshotVersion());interview.setScheduledAt(command.scheduledAt());interview.setRecordingEnabled(job.isRecordingEnabled());interview.setRecordingRetentionDays(job.getRecordingRetentionDays());
        try{interview=interviewRepository.saveAndFlush(interview);}catch(DataIntegrityViolationException e){RecruitmentInterview replay=interviewRepository.findByTenantIdAndApplicationId(command.tenantId(),command.applicationId()).orElseThrow(()->e);return new RecruitmentInterviewCommandApi.CreatedInterview(replay.getId(),replay.getStatus().name(),replay.getTemplateSnapshotSha256());}
        emit(interview,interview.getStatus()==InterviewStatus.SCHEDULED?"interview.scheduled":"interview.invited");
        return new RecruitmentInterviewCommandApi.CreatedInterview(interview.getId(),interview.getStatus().name(),interview.getTemplateSnapshotSha256());
    }

    @Transactional
    public RecruitmentDtos.ApplicationResponse transitionApplication(UUID tenantId,UUID id,ApplicationStatus target){RecruitmentApplication a=applicationRepository.findForUpdate(tenantId,id).orElseThrow(()->notFound("Application"));if(a.getStatus()==target)return queries.application(tenantId,id);ApplicationStatus next=ApplicationLifecycle.transition(a.getStatus(),target);if(next==ApplicationStatus.SUBMITTED){submissionTransition.verifyLocked(a);}else{if(next==ApplicationStatus.WITHDRAWN)a.setWithdrawnAt(now());a.setStatus(next);a=applicationRepository.saveAndFlush(a);emit(a,applicationEvent(next));}if(cvStorageService!=null){if(next==ApplicationStatus.SHORTLISTED||next==ApplicationStatus.REJECTED)cvStorageService.retainTerminal(tenantId,id);else if(next==ApplicationStatus.WITHDRAWN){cvStorageService.scheduleImmediateDeletion(tenantId,id);cvStorageService.deleteNow(tenantId,id);}}return queries.application(tenantId,id);}

    @Transactional
    public RecruitmentDtos.InterviewResponse transitionInterview(UUID tenantId,UUID id,InterviewStatus target){RecruitmentInterview interview=interviewRepository.findForUpdate(tenantId,id).orElseThrow(()->notFound("Interview"));interview.setStatus(InterviewLifecycle.transition(interview.getStatus(),target));if(target==InterviewStatus.COMPLETED)interview.setCompletedAt(now());interview=interviewRepository.saveAndFlush(interview);emit(interview,interviewEvent(target));return queries.interview(tenantId,id);}

    private void validateRecording(UUID tenantId,boolean enabled,int days){if(!enabled){if(days!=0)throw new BadRequestException("Recording retention must be zero when recording is disabled");return;}if(!globalRecordingEnabled)throw conflict("Recording is not enabled for this deployment");BillingPlanCode plan=billingModuleApi.account(tenantId).planCode();int max=switch(plan){case PRO->30;case BUSINESS->90;case STARTER,TRIAL->0;case ENTERPRISE->-1;};if(max<0)throw conflict("Enterprise recording retention is not contracted");if(max==0)throw conflict("Recording is not available on this plan");if(days<1||days>max)throw conflict("Recording retention exceeds the plan limit of "+max+" days");}
    private InterviewTemplateRevision validateRevision(UUID tenantId,UUID id,boolean active){if(id==null)throw conflict("An interview template revision is required");InterviewTemplateRevision r=revisionRepository.findByIdAndTenantId(id,tenantId).orElseThrow(()->conflict("Template revision is not available for this tenant"));InterviewTemplate t=requireTemplate(tenantId,r.getTemplateId());if(active&&t.isArchived())throw conflict("Template revision belongs to an archived template");return r;}
    private void validateJobWrite(UUID tenantId,RecruitmentDtos.JobWrite r,boolean requireActive){requireLocale(r.language());screening.validateAndWrite(r.screeningQuestions());if(requireActive&&(r.closingAt()==null||!r.closingAt().isAfter(now())))throw new BadRequestException("Closing date must be in the future");if(requireActive&&r.templateRevisionId()==null)throw new BadRequestException("Paused jobs require a template revision");if(r.templateRevisionId()!=null)validateRevision(tenantId,r.templateRevisionId(),requireActive);}
    private void apply(RecruitmentJob j,RecruitmentDtos.JobWrite r){j.setTitle(r.title().strip());j.setDescription(r.description().strip());j.setDepartment(trim(r.department()));j.setLocation(trim(r.location()));j.setEmploymentType(r.employmentType());j.setWorkMode(r.workMode());j.setExperienceLevel(r.experienceLevel());j.setLanguage(r.language());j.setCvPolicy(r.cvPolicy());j.setAutomationModeOverride(r.automationModeOverride());j.setCvAiModeOverride(r.cvAiModeOverride());j.setTemplateRevisionId(r.templateRevisionId());j.setClosingAt(r.closingAt());j.setScreeningConfig(screening.validateAndWrite(r.screeningQuestions()));}
    private void apply(RecruitmentCandidate c,RecruitmentDtos.CandidateWrite r){String email=r.email().strip().toLowerCase(Locale.ROOT);c.setFullName(r.fullName().strip());c.setNormalizedName(normalize(r.fullName()));c.setEmail(email);c.setNormalizedEmail(email);c.setPhone(trim(r.phone()));c.setNotes(trim(r.notes()));}
    private InterviewTemplateRevision createRevision(InterviewTemplate t,int number,TemplateSnapshotSupport.Snapshot s){InterviewTemplateRevision r=new InterviewTemplateRevision();r.setTenantId(t.getTenantId());r.setTemplateId(t.getId());r.setRevisionNumber(number);r.setContent(s.json());r.setContentSha256(s.sha256());return revisionRepository.save(r);}
    private RecruitmentJob requireJob(UUID tenantId,UUID id){return jobRepository.findByIdAndTenantId(id,tenantId).orElseThrow(()->notFound("Job"));} private RecruitmentJob lockJob(UUID tenantId,UUID id){return jobRepository.findForUpdate(tenantId,id).orElseThrow(()->notFound("Job"));}
    private InterviewTemplate requireTemplate(UUID tenantId,UUID id){return templateRepository.findByIdAndTenantId(id,tenantId).orElseThrow(()->notFound("Template"));} private RecruitmentCandidate requireCandidate(UUID tenantId,UUID id){return candidateRepository.findByIdAndTenantId(id,tenantId).orElseThrow(()->notFound("Candidate"));}
    private RecruitmentDtos.SettingsResponse settingsResponse(RecruitmentTenantSettings s){return new RecruitmentDtos.SettingsResponse(s.getDefaultAutomationMode(),s.getCvAiMode(),s.getDefaultTemplateRevisionId(),s.isRecordingEnabled(),s.getRecordingRetentionDays(),s.getSchedulingTimezone(),s.getSlotGridMinutes(),s.getMinimumNoticeMinutes(),s.getBookingHorizonDays(),s.getInvitationLifetimeDays(),s.getRescheduleCutoffMinutes(),s.getReminderOffsetsMinutes(),s.getVersion());}
    private void resolveEffectiveSettings(RecruitmentJob job,RecruitmentDtos.SettingsResponse settings){job.setEffectiveAutomationMode(job.getAutomationModeOverride()!=null?job.getAutomationModeOverride():settings.defaultAutomationMode());job.setEffectiveCvAiMode(job.getCvAiModeOverride()!=null?job.getCvAiModeOverride():settings.cvAiMode());job.setRecordingEnabled(settings.recordingEnabled());job.setRecordingRetentionDays(settings.recordingRetentionDays());}
    private RecruitmentDtos.JobResponse jobResponse(RecruitmentJob j){return new RecruitmentDtos.JobResponse(j.getId(),j.getPublicId(),j.getTitle(),j.getDescription(),j.getDepartment(),j.getLocation(),j.getEmploymentType(),j.getWorkMode(),j.getExperienceLevel(),j.getLanguage(),j.getStatus(),j.getCvPolicy(),j.getAutomationModeOverride(),j.getCvAiModeOverride(),j.getEffectiveAutomationMode(),j.getEffectiveCvAiMode(),j.isRecordingEnabled(),j.getRecordingRetentionDays(),j.getTemplateRevisionId(),j.getClosingAt(),j.getPublishedAt(),j.getPausedAt(),j.getClosedAt(),j.getArchivedAt(),j.getActiveJobReservationId(),j.getFrozenCompanyName(),j.getFrozenCompanySlug(),j.getVersion(),screening.read(j.getScreeningConfig()),j.getCreatedAt(),j.getUpdatedAt());}
    private RecruitmentDtos.TemplateResponse templateResponse(InterviewTemplate t,int latest){return new RecruitmentDtos.TemplateResponse(t.getId(),t.getName(),t.getDescription(),t.getLocale(),t.isArchived(),t.getArchivedAt(),latest,t.getVersion(),t.getCreatedAt(),t.getUpdatedAt());}
    private RecruitmentDtos.RevisionResponse revisionResponse(InterviewTemplateRevision r){return new RecruitmentDtos.RevisionResponse(r.getId(),r.getTemplateId(),r.getRevisionNumber(),new TemplateSnapshotSupport(objectMapper).read(r.getContent()),r.getContentSha256(),r.getCreatedAt());}
    private RecruitmentDtos.CandidateResponse candidateResponse(RecruitmentCandidate c){return new RecruitmentDtos.CandidateResponse(c.getId(),c.getFullName(),c.getEmail(),c.getPhone(),c.getNotes(),c.getVersion(),c.getCreatedAt(),c.getUpdatedAt());}
    private static String normalize(String v){return Normalizer.normalize(v.strip().toLowerCase(Locale.ROOT),Normalizer.Form.NFKC).replaceAll("\\s+"," ");} private static String trim(String v){return v==null||v.isBlank()?null:v.strip();}
    private static void requireLocale(String locale){if(!"vi-VN".equals(locale)&&!"en-US".equals(locale))throw new BadRequestException("Locale must be vi-VN or en-US");}
    private void publishProjection(RecruitmentJob job,boolean visible){eventPublisher.publishEvent(new PublicJobProjectionChangedEvent(job.getTenantId(),job.getId(),visible));}
    private void emit(RecruitmentJob value,String event){if(projectionEvents!=null)projectionEvents.job(value,event);}
    private void emit(RecruitmentApplication value,String event){if(projectionEvents!=null)projectionEvents.application(value,event);}
    private void emit(RecruitmentInterview value,String event){if(projectionEvents!=null)projectionEvents.interview(value,event);}
    private static String applicationEvent(ApplicationStatus status){return switch(status){case WITHDRAWN->"application.withdrawn";case UNDER_REVIEW->"application.under_review";case SHORTLISTED->"application.shortlisted";case REJECTED->"application.rejected";default->null;};}
    private static String interviewEvent(InterviewStatus status){return switch(status){case INVITED->"interview.invited";case SCHEDULED->"interview.scheduled";case IN_PROGRESS->"interview.started";case COMPLETED->"interview.completed";case FAILED->"interview.failed";case NO_ANSWER->"interview.no_answer";case DECLINED->"interview.declined";case CANCELLED->"interview.cancelled";case EXPIRED->"interview.expired";default->null;};}
    private LocalDateTime now(){return LocalDateTime.now(clock);} private static ConflictException conflict(String message){return new ConflictException(message);} private static ResourceNotFoundException notFound(String name){return new ResourceNotFoundException(name+" was not found");}
}
