package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.model.RecruitmentEnums.ApplicationStatus;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CvAiMode;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CvPolicy;
import com.cacanode.api.recruitment.query.CandidateAccessService;
import com.cacanode.api.recruitment.query.RecruitmentCvStorageService;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import com.cacanode.api.recruitment.repository.RecruitmentCandidateRepository;
import com.cacanode.api.recruitment.repository.RecruitmentJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class CandidateCompletionService {
    private final CandidateAccessService access;
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentCandidateRepository candidates;
    private final RecruitmentJobRepository jobs;
    private final ScreeningSupport screening;
    private final RecruitmentPhoneNumbers phoneNumbers;
    private final RecruitmentCvStorageService cvs;
    private final ApplicationSubmissionTransitionService submissionTransition;
    private final Clock clock;

    @Transactional(readOnly=true)
    public PublicRecruitmentDtos.CandidateCompletionDetails details(String rawAccess) {
        var subject=access.authorizeCompletion(rawAccess,null,false);
        var application=applications.findByIdAndTenantId(subject.applicationId(),subject.tenantId())
                .orElseThrow(()->new ResourceNotFoundException("Application was not found"));
        var candidate=candidates.findByIdAndTenantId(subject.candidateId(),subject.tenantId())
                .orElseThrow(()->new ResourceNotFoundException("Candidate was not found"));
        var job=jobs.findByIdAndTenantId(subject.jobId(),subject.tenantId())
                .orElseThrow(()->new ResourceNotFoundException("Job was not found"));
        return new PublicRecruitmentDtos.CandidateCompletionDetails(application.getId(),job.getFrozenCompanyName(),
                job.getTitle(),candidate.getFullName(),candidate.getEmail(),candidate.getPhone(),application.getLocale(),
                job.getCvPolicy(),application.getCvAiModeSnapshot(),screening.publicQuestions(application.getScreeningConfigSnapshot()));
    }

    @Transactional
    public PublicRecruitmentDtos.CandidateApplication complete(String rawAccess,String csrf,
            PublicRecruitmentDtos.CandidateCompletionData data,MultipartFile cv) {
        var subject=access.authorizeCompletion(rawAccess,csrf,true);
        var application=applications.findForUpdate(subject.tenantId(),subject.applicationId())
                .orElseThrow(()->new ResourceNotFoundException("Application was not found"));
        if(application.getStatus()!=ApplicationStatus.AWAITING_CANDIDATE)
            throw new ConflictException("APPLICATION_COMPLETION_NOT_PENDING");
        var candidate=candidates.findForUpdate(subject.tenantId(),subject.candidateId())
                .orElseThrow(()->new ResourceNotFoundException("Candidate was not found"));
        var job=jobs.findByIdAndTenantId(subject.jobId(),subject.tenantId())
                .orElseThrow(()->new ResourceNotFoundException("Job was not found"));
        boolean cvPresent=cv!=null&&!cv.isEmpty();validateCv(job.getCvPolicy(),cvPresent);
        boolean cvAiApplies=cvPresent&&application.getCvAiModeSnapshot()!=CvAiMode.OFF;
        if(cvAiApplies&&!data.cvUseConsent())throw new BadRequestException("CV-use consent is required");
        String answers=screening.validateAnswers(application.getScreeningConfigSnapshot(),data.screeningAnswers());
        RecruitmentCvStorageService.StagedCv staged=cvPresent?cvs.stage(subject.tenantId(),subject.jobId(),cv):null;
        try {
            LocalDateTime now=LocalDateTime.now(clock);
            candidate.setFullName(data.fullName().strip());candidate.setNormalizedName(normalize(data.fullName()));
            candidate.setPhone(phoneNumbers.normalizeRequired(data.phone()));candidates.save(candidate);
            application.setLocale(data.locale());application.setSubmittedAt(now);application.setPrivacyConsentAt(now);
            application.setCvUseDisclosedAt(cvAiApplies?now:null);application.setCvAiConsentAt(cvAiApplies?now:null);
            application.setCvPresent(cvPresent);application.setScreeningAnswers(answers);
            application.setStatus(ApplicationStatus.SUBMITTED_UNVERIFIED);applications.saveAndFlush(application);
            if(staged!=null)cvs.promote(staged,application.getId());
            submissionTransition.verifyLocked(application);
            return access.me(rawAccess);
        } catch(RuntimeException failure) {
            if(staged!=null)cvs.discard(staged);throw failure;
        }
    }

    private static void validateCv(CvPolicy policy,boolean present){
        if(policy==CvPolicy.DISABLED&&present)throw new BadRequestException("This job does not accept CV files");
        if(policy==CvPolicy.REQUIRED&&!present)throw new BadRequestException("A CV file is required");
    }
    private static String normalize(String value){return Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT),Normalizer.Form.NFKC).replaceAll("\\s+"," ");}
}
