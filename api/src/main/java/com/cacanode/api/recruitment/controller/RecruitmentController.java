package com.cacanode.api.recruitment.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.query.RecruitmentQueryService;
import com.cacanode.api.recruitment.query.RecruitmentCvAnalysisQueryService;
import com.cacanode.api.recruitment.service.RecruitmentService;
import com.cacanode.api.recruitment.service.InterviewInvitationService;
import com.cacanode.api.recruitment.service.RecruitmentAvailabilityService;
import com.cacanode.api.recruitment.service.RecruitmentCvAnalysisService;
import com.cacanode.api.recruitment.service.RecruitmentApplicationCompletionLinkService;
import com.cacanode.api.recruitment.service.PublicInterviewSchedulingService;
import com.cacanode.api.recruitment.service.RecruitmentInterviewCancellationService;
import com.cacanode.api.recruitment.api.RecruitmentApplicationCommandApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recruitment")
@Validated
@PreAuthorize("hasAnyRole('USER','TENANT_ADMIN')")
@ConditionalOnProperty(prefix = "app.recruitment", name = "enabled", havingValue = "true")
public class RecruitmentController extends BaseController {
    private final RecruitmentService service;
    private final RecruitmentQueryService queries;
    private final InterviewInvitationService invitations;
    private final RecruitmentAvailabilityService availability;
    private final RecruitmentApplicationCompletionLinkService completionLinks;
    private final PublicInterviewSchedulingService scheduling;
    private final RecruitmentInterviewCancellationService cancellations;
    @Autowired(required=false) private RecruitmentCvAnalysisQueryService cvAnalyses;
    @Autowired(required=false) private RecruitmentCvAnalysisService cvAnalysisCommands;

    @Autowired
    public RecruitmentController(RecruitmentService service,RecruitmentQueryService queries,
            InterviewInvitationService invitations,RecruitmentAvailabilityService availability,
            RecruitmentApplicationCompletionLinkService completionLinks,
            PublicInterviewSchedulingService scheduling,
            RecruitmentInterviewCancellationService cancellations){
        this.service=service;this.queries=queries;this.invitations=invitations;this.availability=availability;
        this.completionLinks=completionLinks;this.scheduling=scheduling;this.cancellations=cancellations;
    }

    RecruitmentController(RecruitmentService service,RecruitmentQueryService queries){this(service,queries,null,null,null,null,null);}

    @GetMapping("/settings") public RecruitmentDtos.SettingsResponse settings(HttpServletRequest r){return service.settings(getTenantId(r));}
    @GetMapping("/overview") public RecruitmentDtos.OverviewResponse overview(HttpServletRequest r){return queries.overview(getTenantId(r));}
    @GetMapping("/applications/{applicationId}/cv-analysis")
    public RecruitmentDtos.CvAnalysisResponse cvAnalysis(@PathVariable UUID applicationId,HttpServletRequest r){
        return cvAnalyses.get(getTenantId(r),applicationId);
    }
    @PostMapping("/applications/{applicationId}/cv-analysis/refresh")
    public RecruitmentDtos.CvAnalysisResponse refreshCvAnalysis(@PathVariable UUID applicationId,
            @Valid @RequestBody RecruitmentDtos.CvAnalysisRefreshRequest body,HttpServletRequest r){
        if(cvAnalysisCommands==null)throw new com.cacanode.api.common.exception.custom.ConflictException("CV_ANALYSIS_REFRESH_DISABLED");
        cvAnalysisCommands.refresh(getTenantId(r),applicationId,body.requestId());
        return cvAnalyses.get(getTenantId(r),applicationId);
    }
    @PutMapping("/settings") @PreAuthorize("hasRole('TENANT_ADMIN')") public RecruitmentDtos.SettingsResponse updateSettings(@Valid @RequestBody RecruitmentDtos.SettingsUpdate body,HttpServletRequest r){return service.updateSettings(getTenantId(r),body);}
    @GetMapping("/availability") @PreAuthorize("hasRole('TENANT_ADMIN')") public RecruitmentDtos.AvailabilityResponse availability(HttpServletRequest r){return availability.get(getTenantId(r));}
    @PutMapping("/availability") @PreAuthorize("hasRole('TENANT_ADMIN')") public RecruitmentDtos.AvailabilityResponse replaceAvailability(@Valid @RequestBody RecruitmentDtos.AvailabilityUpdate body,HttpServletRequest r){return availability.replace(getTenantId(r),body);}

    @GetMapping("/jobs")
    public ResponseEntity<List<RecruitmentDtos.JobResponse>> jobs(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) JobStatus status,@RequestParam(required=false) String department,@RequestParam(required=false) String location,
            @RequestParam(required=false) String employmentType,@RequestParam(required=false) String workMode,@RequestParam(required=false) String language,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime closingFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime closingTo,
            @RequestParam(required=false,name="q") String search,@RequestParam(required=false) String sort,@RequestParam(required=false) String direction,HttpServletRequest r){
        var result=queries.jobs(getTenantId(r),page,size,status,department,location,employmentType,workMode,language,closingFrom,closingTo,search,sort,direction);return listed(result);}
    @PostMapping("/jobs") public ResponseEntity<RecruitmentDtos.JobResponse> createJob(@Valid @RequestBody RecruitmentDtos.JobWrite body,HttpServletRequest r){var response=service.createJob(getTenantId(r),body);return created("/api/v1/recruitment/jobs/"+response.id(),response);}
    @GetMapping("/jobs/{id}") public RecruitmentDtos.JobResponse job(@PathVariable UUID id,HttpServletRequest r){return service.job(getTenantId(r),id);}
    @GetMapping("/jobs/{id}/preview") public ResponseEntity<RecruitmentDtos.JobPreview> preview(@PathVariable UUID id,HttpServletRequest r){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(getTenantId(r),id));}
    @PutMapping("/jobs/{id}") public RecruitmentDtos.JobResponse updateJob(@PathVariable UUID id,@Valid @RequestBody RecruitmentDtos.JobWrite body,HttpServletRequest r){return service.updateJob(getTenantId(r),id,body);}
    @DeleteMapping("/jobs/{id}") public ResponseEntity<Void> deleteJob(@PathVariable UUID id,HttpServletRequest r){service.deleteJob(getTenantId(r),id);return ResponseEntity.noContent().build();}
    @PostMapping("/jobs/{id}/publish") public RecruitmentDtos.JobResponse publish(@PathVariable UUID id,HttpServletRequest r){return service.publish(getTenantId(r),id);}
    @PostMapping("/jobs/{id}/pause") public RecruitmentDtos.JobResponse pause(@PathVariable UUID id,HttpServletRequest r){return service.pause(getTenantId(r),id);}
    @PostMapping("/jobs/{id}/close") public RecruitmentDtos.JobResponse close(@PathVariable UUID id,HttpServletRequest r){return service.close(getTenantId(r),id);}
    @PostMapping("/jobs/{id}/archive") public RecruitmentDtos.JobResponse archive(@PathVariable UUID id,HttpServletRequest r){return service.archive(getTenantId(r),id);}

    @GetMapping("/templates") public ResponseEntity<List<RecruitmentDtos.TemplateResponse>> templates(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String locale,@RequestParam(required=false) Boolean archived,@RequestParam(required=false,name="q") String search,@RequestParam(required=false) String sort,@RequestParam(required=false) String direction,HttpServletRequest r){return listed(queries.templates(getTenantId(r),page,size,locale,archived,search,sort,direction));}
    @PostMapping("/templates") public ResponseEntity<RecruitmentDtos.TemplateResponse> createTemplate(@Valid @RequestBody RecruitmentDtos.TemplateCreate body,HttpServletRequest r){var response=service.createTemplate(getTenantId(r),body);return created("/api/v1/recruitment/templates/"+response.id(),response);}
    @GetMapping("/templates/{id}") public RecruitmentDtos.TemplateResponse template(@PathVariable UUID id,HttpServletRequest r){return service.template(getTenantId(r),id);}
    @PatchMapping("/templates/{id}") public RecruitmentDtos.TemplateResponse patchTemplate(@PathVariable UUID id,@Valid @RequestBody RecruitmentDtos.TemplatePatch body,HttpServletRequest r){return service.patchTemplate(getTenantId(r),id,body);}
    @DeleteMapping("/templates/{id}") public ResponseEntity<Void> archiveTemplate(@PathVariable UUID id,HttpServletRequest r){service.archiveTemplate(getTenantId(r),id);return ResponseEntity.noContent().build();}
    @GetMapping("/templates/{id}/revisions") public List<RecruitmentDtos.RevisionResponse> revisions(@PathVariable UUID id,HttpServletRequest r){return service.revisions(getTenantId(r),id);}
    @PostMapping("/templates/{id}/revisions") public ResponseEntity<RecruitmentDtos.RevisionResponse> addRevision(@PathVariable UUID id,@Valid @RequestBody RecruitmentDtos.RevisionCreate body,HttpServletRequest r){var response=service.addRevision(getTenantId(r),id,body);return created("/api/v1/recruitment/templates/"+id+"/revisions/"+response.id(),response);}
    @GetMapping("/templates/{id}/revisions/{revisionId}") public RecruitmentDtos.RevisionResponse revision(@PathVariable UUID id,@PathVariable UUID revisionId,HttpServletRequest r){return service.revision(getTenantId(r),id,revisionId);}

    @GetMapping("/candidates") public ResponseEntity<List<RecruitmentDtos.CandidateResponse>> candidates(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) UUID jobId,@RequestParam(required=false,name="q") String search,@RequestParam(required=false) String sort,@RequestParam(required=false) String direction,HttpServletRequest r){return listed(queries.candidates(getTenantId(r),page,size,jobId,search,sort,direction));}
    @PostMapping("/candidates") public ResponseEntity<RecruitmentDtos.CandidateResponse> createCandidate(@Valid @RequestBody RecruitmentDtos.CandidateWrite body,HttpServletRequest r){var response=service.createCandidate(getTenantId(r),body);return created("/api/v1/recruitment/candidates/"+response.id(),response);}
    @GetMapping("/candidates/{id}") public RecruitmentDtos.CandidateResponse candidate(@PathVariable UUID id,HttpServletRequest r){return service.candidate(getTenantId(r),id);}
    @PutMapping("/candidates/{id}") public RecruitmentDtos.CandidateResponse updateCandidate(@PathVariable UUID id,@Valid @RequestBody RecruitmentDtos.CandidateWrite body,HttpServletRequest r){return service.updateCandidate(getTenantId(r),id,body);}
    @DeleteMapping("/candidates/{id}") public ResponseEntity<Void> deleteCandidate(@PathVariable UUID id,HttpServletRequest r){service.deleteCandidate(getTenantId(r),id);return ResponseEntity.noContent().build();}

    @GetMapping("/applications") public ResponseEntity<List<RecruitmentDtos.ApplicationResponse>> applications(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) ApplicationStatus status,@RequestParam(required=false) UUID jobId,@RequestParam(required=false) UUID candidateId,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedFrom,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime submittedTo,@RequestParam(required=false) Boolean cvPresent,@RequestParam(required=false) CvAnalysisStatus cvAnalysisStatus,@RequestParam(required=false) InterviewStatus interviewStatus,@RequestParam(required=false) BigDecimal scoreMin,@RequestParam(required=false) BigDecimal scoreMax,@RequestParam(required=false) String englishBand,@RequestParam(required=false,name="q") String search,@RequestParam(required=false) String sort,@RequestParam(required=false) String direction,HttpServletRequest r){return listed(queries.applications(getTenantId(r),page,size,status,jobId,candidateId,submittedFrom,submittedTo,cvPresent,cvAnalysisStatus,interviewStatus,scoreMin,scoreMax,englishBand,search,sort,direction));}
    @PostMapping("/applications") public ResponseEntity<RecruitmentDtos.ApplicationResponse> createApplication(@Valid @RequestBody RecruitmentDtos.ApplicationCreate body,HttpServletRequest r){UUID tenantId=getTenantId(r);var created=service.create(new RecruitmentApplicationCommandApi.CreateApplicationCommand(tenantId,body.jobId(),body.candidateId(),false));var response=queries.application(tenantId,created.applicationId());return created("/api/v1/recruitment/applications/"+response.id(),response);}
    @PostMapping("/applications/{id}/completion-link") public RecruitmentDtos.CompletionLinkResponse completionLink(@PathVariable UUID id,HttpServletRequest r){return completionLinks.send(getTenantId(r),id);}
    @GetMapping("/applications/{id}") public RecruitmentDtos.ApplicationResponse application(@PathVariable UUID id,HttpServletRequest r){return queries.application(getTenantId(r),id);}
    @GetMapping("/applications/{id}/detail") public RecruitmentDtos.ApplicationDetailResponse applicationDetail(@PathVariable UUID id,HttpServletRequest r){return queries.applicationDetail(getTenantId(r),id);}
    @PostMapping("/applications/{id}/transitions") public RecruitmentDtos.ApplicationResponse transition(@PathVariable UUID id,@Valid @RequestBody RecruitmentDtos.TransitionRequest body,HttpServletRequest r){return service.transitionApplication(getTenantId(r),id,body.targetStatus());}
    @PostMapping("/applications/{id}/invite") public RecruitmentDtos.InterviewResponse invite(@PathVariable UUID id,HttpServletRequest r){var interview=invitations.invite(getTenantId(r),id,true);return queries.interview(getTenantId(r),interview.getId());}

    @GetMapping("/interviews") public ResponseEntity<List<RecruitmentDtos.InterviewResponse>> interviews(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) InterviewStatus status,@RequestParam(required=false) UUID jobId,@RequestParam(required=false) UUID applicationId,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,@RequestParam(required=false) BigDecimal scoreMin,@RequestParam(required=false) BigDecimal scoreMax,@RequestParam(required=false) String englishBand,@RequestParam(required=false,name="q") String search,@RequestParam(required=false) String sort,@RequestParam(required=false) String direction,HttpServletRequest r){return listed(queries.interviews(getTenantId(r),page,size,status,jobId,applicationId,dateFrom,dateTo,scoreMin,scoreMax,englishBand,search,sort,direction));}
    @GetMapping("/interviews/{id}") public RecruitmentDtos.InterviewResponse interview(@PathVariable UUID id,HttpServletRequest r){return queries.interview(getTenantId(r),id);}
    @GetMapping("/interviews/{id}/attempts") public List<RecruitmentDtos.CallAttemptResponse> attempts(@PathVariable UUID id,HttpServletRequest r){return queries.attempts(getTenantId(r),id);}
    @GetMapping("/interviews/{id}/slots") public PublicRecruitmentDtos.SlotPage interviewSlots(@PathVariable UUID id,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(defaultValue="14") int days,HttpServletRequest r){return scheduling.tenantSlots(getTenantId(r),id,from,days);}
    @PostMapping("/interviews/{id}/schedule") public RecruitmentDtos.InterviewResponse scheduleInterview(@PathVariable UUID id,@Valid @RequestBody PublicRecruitmentDtos.ScheduleRequest body,HttpServletRequest r){UUID tenantId=getTenantId(r);scheduling.tenantSchedule(tenantId,id,body.startAt(),false);return queries.interview(tenantId,id);}
    @PostMapping("/interviews/{id}/reschedule") public RecruitmentDtos.InterviewResponse rescheduleInterview(@PathVariable UUID id,@Valid @RequestBody PublicRecruitmentDtos.ScheduleRequest body,HttpServletRequest r){UUID tenantId=getTenantId(r);scheduling.tenantSchedule(tenantId,id,body.startAt(),true);return queries.interview(tenantId,id);}
    @PostMapping("/interviews/{id}/cancel") public RecruitmentDtos.InterviewResponse cancelInterview(@PathVariable UUID id,HttpServletRequest r){UUID tenantId=getTenantId(r);cancellations.cancelByRecruiter(tenantId,id);return queries.interview(tenantId,id);}
    @PostMapping("/interviews/{id}/reinvite") public RecruitmentDtos.InterviewResponse reinviteInterview(@PathVariable UUID id,HttpServletRequest r){UUID tenantId=getTenantId(r);invitations.reinvite(tenantId,id);return queries.interview(tenantId,id);}
    @GetMapping("/interviews/{id}/delivery-history") public List<RecruitmentDtos.DeliveryHistoryResponse> deliveryHistory(@PathVariable UUID id,HttpServletRequest r){return queries.deliveryHistory(getTenantId(r),id);}

    private static <T> ResponseEntity<List<T>> listed(RecruitmentDtos.PageResult<T> result){return ResponseEntity.ok().header("X-Total-Count",Long.toString(result.totalCount())).body(result.items());}
    private static <T> ResponseEntity<T> created(String location,T body){return ResponseEntity.created(URI.create(location)).body(body);}
}
