package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.recruitment.api.InterviewEventIdentity;
import com.cacanode.api.recruitment.api.event.ResumeAnalysisRequestedEvent;
import com.cacanode.api.recruitment.config.CvAnalysisProperties;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.messaging-enabled:false} and ${app.recruitment.cv-ai-enabled:false}")
public class RecruitmentCvAnalysisService {
    private static final Set<ApplicationStatus> TERMINAL=Set.of(
            ApplicationStatus.INTERVIEW_COMPLETED,ApplicationStatus.SHORTLISTED,
            ApplicationStatus.REJECTED,ApplicationStatus.WITHDRAWN);
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentApplicationCvRepository cvs;
    private final RecruitmentCvAnalysisRepository analyses;
    private final RecruitmentJobRepository jobs;
    private final HiringQuotaApi quota;
    private final CvAnalysisProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private RecruitmentCapabilityService capabilities;

    @Transactional
    public void process(UUID tenantId,UUID applicationId) {
        if(capabilities!=null&&!capabilities.capabilities(tenantId).cvAiEnabled()){cancel(tenantId,applicationId);return;}
        RecruitmentApplication application=applications.findForUpdate(tenantId,applicationId).orElse(null);
        if(application==null || application.getActiveCvAnalysisId()!=null || TERMINAL.contains(application.getStatus())
                || application.getCvAiModeSnapshot()==CvAiMode.OFF || application.getCvAiConsentAt()==null)return;
        RecruitmentApplicationCv cv=cvs.findActiveForUpdate(tenantId,applicationId).orElse(null);
        if(cv==null || cv.getStorageState()!=CvStorageState.PROMOTED || cv.getPromotedObjectKey()==null)return;
        UUID analysisId=InterviewEventIdentity.resumeAnalysisId(tenantId,applicationId,cv.getContentSha256(),
                application.getCvAiModeSnapshot().name(),application.getCvAiPolicyVersion(),application.getCvAiModelVersion());
        RecruitmentCvAnalysis existing=analyses.findByIdAndTenantId(analysisId,tenantId).orElse(null);
        if(existing!=null){activate(application,existing);return;}

        RecruitmentCvAnalysis analysis=base(application,cv,analysisId);
        LocalDateTime now=LocalDateTime.now(clock);
        try {
            quota.consumeCvAnalysis(tenantId,analysisId);
        } catch (HiringQuotaApi.HiringQuotaExceededException exhausted) {
            analysis.setStatus(CvAnalysisRecordStatus.SKIPPED_QUOTA);
            analysis.setFailureCode("CV_ANALYSIS_QUOTA_EXCEEDED");
            analysis.setCompletedAt(now);
            analyses.saveAndFlush(analysis);
            application.setCvAnalysisStatus(CvAnalysisStatus.SKIPPED_QUOTA);
            application.setActiveCvAnalysisId(analysisId);
            applications.save(application);
            return;
        }
        ResumeAnalysisRequestedEvent request=request(application,cv,analysisId,now);
        byte[] payload=write(request);
        analysis.setStatus(CvAnalysisRecordStatus.QUEUED);
        analysis.setRequestEventId(request.eventId());
        analysis.setRequestPayload(new String(payload,StandardCharsets.UTF_8));
        analysis.setRequestPayloadSha256(sha256(payload));
        analysis.setNextPublishAt(now);
        analyses.saveAndFlush(analysis);
        application.setCvAnalysisStatus(CvAnalysisStatus.PENDING);
        application.setActiveCvAnalysisId(analysisId);
        applications.save(application);
    }

    @Transactional
    public void cancel(UUID tenantId,UUID applicationId) {
        applications.findForUpdate(tenantId,applicationId).ifPresent(application->{
            if(application.getActiveCvAnalysisId()!=null){
                application.setCvAnalysisStatus(CvAnalysisStatus.CANCELLED);
                application.setActiveCvAnalysisId(null);
                applications.saveAndFlush(application);
            }
        });
        analyses.cancelApplication(tenantId,applicationId);
        analyses.deleteApplication(tenantId,applicationId);
    }

    private RecruitmentCvAnalysis base(RecruitmentApplication application,RecruitmentApplicationCv cv,UUID id){
        RecruitmentCvAnalysis value=new RecruitmentCvAnalysis();value.setId(id);value.setTenantId(application.getTenantId());
        value.setApplicationId(application.getId());value.setCvId(cv.getId());value.setCvSha256(cv.getContentSha256());
        value.setAnalysisMode(application.getCvAiModeSnapshot());value.setPolicyVersion(application.getCvAiPolicyVersion());
        value.setModelVersion(application.getCvAiModelVersion());return value;
    }

    private void activate(RecruitmentApplication application,RecruitmentCvAnalysis analysis){
        application.setActiveCvAnalysisId(analysis.getId());
        application.setCvAnalysisStatus(switch(analysis.getStatus()){
            case QUEUED,PUBLISHED->CvAnalysisStatus.PENDING;case COMPLETED->CvAnalysisStatus.COMPLETED;
            case FAILED->CvAnalysisStatus.FAILED;case SKIPPED_QUOTA->CvAnalysisStatus.SKIPPED_QUOTA;
            case CANCELLED->CvAnalysisStatus.CANCELLED;});applications.save(application);
    }

    private ResumeAnalysisRequestedEvent request(RecruitmentApplication application,RecruitmentApplicationCv cv,
            UUID analysisId,LocalDateTime now){
        RecruitmentJob job=jobs.findByIdAndTenantId(application.getJobId(),application.getTenantId()).orElseThrow();
        List<UUID> sectionIds=new ArrayList<>();List<ResumeAnalysisRequestedEvent.TemplateQuestion> questions=new ArrayList<>();
        try{
            JsonNode sections=mapper.readTree(application.getTemplateSnapshot()).path("sections");
            for(JsonNode section:sections){if(!"CORE".equals(section.path("kind").asText()))continue;
                UUID sectionId=UUID.fromString(section.path("sectionId").asText());sectionIds.add(sectionId);
                for(JsonNode question:section.path("questions"))questions.add(new ResumeAnalysisRequestedEvent.TemplateQuestion(
                        UUID.fromString(question.path("questionId").asText()),sectionId,
                        bounded(question.path("prompt").asText(),1000),bounded(question.path("competency").asText(),200)));
            }
        }catch(Exception exception){throw new IllegalStateException("Stored template snapshot is invalid",exception);}
        int limit=application.getCvAiModeSnapshot()==CvAiMode.PERSONALIZED_QUESTIONS
                ?properties.maxPersonalizedQuestions():0;
        return new ResumeAnalysisRequestedEvent("1.1",
                InterviewEventIdentity.eventId("interview.resume-analysis.requested",analysisId,"requested:v1.1"),
                "interview.resume-analysis.requested",now.toInstant(ZoneOffset.UTC),application.getTenantId(),analysisId,
                analysisId,application.getId(),cv.getId(),cv.getPromotedObjectKey(),cv.getOriginalFilename(),
                cv.getContentType(),cv.getByteSize(),application.getLocale(),cv.getContentSha256(),
                application.getCvAiModeSnapshot().name(),application.getCvAiPolicyVersion(),application.getCvAiModelVersion(),
                bounded(job.getTitle(),200),bounded(job.getDescription(),4000),List.copyOf(sectionIds),List.copyOf(questions),limit);
    }

    private byte[] write(Object value){try{return mapper.writeValueAsBytes(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private static String bounded(String value,int max){if(value==null)return "";String v=value.strip();return v.length()<=max?v:v.substring(0,max);}
    static String sha256(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
}
