package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.common.exception.custom.ConflictException;
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
    private static final String PIPELINE_RETRY_SUFFIX="+pipeline-v2";
    private static final Set<ApplicationStatus> TERMINAL=Set.of(
            ApplicationStatus.INTERVIEW_COMPLETED,ApplicationStatus.SHORTLISTED,
            ApplicationStatus.REJECTED,ApplicationStatus.WITHDRAWN);
    private static final Set<String> RETRYABLE_LEGACY_FAILURES=Set.of(
            "CV_ANALYSIS_INVALID_MODEL_OUTPUT","CV_ANALYSIS_UNGROUNDED_EVIDENCE",
            "CV_ANALYSIS_INVALID_SKILL_EVIDENCE","CV_ANALYSIS_INVALID_QUESTION",
            "CV_ANALYSIS_TOO_MANY_QUESTIONS","CV_ANALYSIS_QUESTIONS_NOT_ALLOWED",
            "CV_ANALYSIS_PROTECTED_DATA_LEAKAGE","CV_ANALYSIS_RETRY_EXHAUSTED");
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
        queue(application,cv,properties.policyVersion(),properties.modelVersion(),1,null,true,false);
    }

    @Transactional
    public void processRetryableLegacyFailure(UUID tenantId,UUID applicationId) {
        if(capabilities!=null&&!capabilities.capabilities(tenantId).cvAiEnabled())return;
        RecruitmentApplication application=applications.findForUpdate(tenantId,applicationId).orElse(null);
        if(application==null || application.getActiveCvAnalysisId()==null || TERMINAL.contains(application.getStatus())
                || application.getCvAiModeSnapshot()==CvAiMode.OFF || application.getCvAiConsentAt()==null)return;
        RecruitmentCvAnalysis failed=analyses.findForUpdate(tenantId,application.getActiveCvAnalysisId()).orElse(null);
        if(failed==null || failed.getStatus()!=CvAnalysisRecordStatus.FAILED
                || !RETRYABLE_LEGACY_FAILURES.contains(failed.getFailureCode())
                || !properties.modelVersion().equals(failed.getModelVersion()))return;
        RecruitmentApplicationCv cv=cvs.findActiveForUpdate(tenantId,applicationId).orElse(null);
        if(cv==null || cv.getStorageState()!=CvStorageState.PROMOTED || cv.getPromotedObjectKey()==null)return;
        String retryModelVersion=failed.getModelVersion()+PIPELINE_RETRY_SUFFIX;
        int revision=Math.max(failed.getAnalysisRevision()+1,analyses.maxRevision(tenantId,applicationId)+1);
        UUID currentId=analysisId(application,cv,failed.getPolicyVersion(),retryModelVersion,revision);
        if(currentId.equals(failed.getId()))return;
        queue(application,cv,failed.getPolicyVersion(),retryModelVersion,revision,null,false,false);
    }

    @Transactional
    public UUID refresh(UUID tenantId,UUID applicationId,UUID requestId) {
        if(requestId==null)throw new ConflictException("CV_ANALYSIS_REFRESH_REQUEST_ID_REQUIRED");
        if(capabilities!=null&&!capabilities.capabilities(tenantId).cvAiEnabled())
            throw new ConflictException("CV_ANALYSIS_REFRESH_DISABLED");
        RecruitmentApplication application=applications.findForUpdate(tenantId,applicationId)
                .orElseThrow(()->new com.cacanode.api.common.exception.custom.ResourceNotFoundException("Application was not found"));
        RecruitmentCvAnalysis replay=analyses.findByTenantIdAndApplicationIdAndRefreshRequestId(
                tenantId,applicationId,requestId).orElse(null);
        if(replay!=null)return replay.getId();
        RecruitmentCvAnalysis pending=application.getPendingCvAnalysisId()==null?null:
                analyses.findForUpdate(tenantId,application.getPendingCvAnalysisId()).orElse(null);
        if(pending!=null&&(pending.getStatus()==CvAnalysisRecordStatus.QUEUED
                ||pending.getStatus()==CvAnalysisRecordStatus.PUBLISHED))return pending.getId();
        RecruitmentCvAnalysis current=application.getActiveCvAnalysisId()==null?null:
                analyses.findForUpdate(tenantId,application.getActiveCvAnalysisId()).orElse(null);
        if(current==null||!Set.of(CvAnalysisRecordStatus.COMPLETED,CvAnalysisRecordStatus.FAILED).contains(current.getStatus())
                ||TERMINAL.contains(application.getStatus())||application.getCvAiModeSnapshot()==CvAiMode.OFF
                ||application.getCvAiConsentAt()==null)throw new ConflictException("CV_ANALYSIS_REFRESH_NOT_ELIGIBLE");
        RecruitmentApplicationCv cv=cvs.findActiveForUpdate(tenantId,applicationId).orElse(null);
        if(cv==null||cv.getStorageState()!=CvStorageState.PROMOTED||cv.getPromotedObjectKey()==null)
            throw new ConflictException("CV_ANALYSIS_REFRESH_CV_UNAVAILABLE");
        int revision=analyses.maxRevision(tenantId,applicationId)+1;
        return queue(application,cv,properties.policyVersion(),properties.modelVersion(),revision,requestId,true,true);
    }

    private UUID queue(RecruitmentApplication application,RecruitmentApplicationCv cv,
            String policyVersion,String modelVersion,int revision,UUID refreshRequestId,
            boolean consumeQuota,boolean refresh) {
        UUID analysisId=analysisId(application,cv,policyVersion,modelVersion,revision);
        UUID tenantId=application.getTenantId();
        RecruitmentCvAnalysis existing=analyses.findByIdAndTenantId(analysisId,tenantId).orElse(null);
        if(existing!=null){activate(application,existing,refresh);return existing.getId();}

        RecruitmentCvAnalysis analysis=base(application,cv,analysisId,policyVersion,modelVersion,revision,refreshRequestId);
        LocalDateTime now=LocalDateTime.now(clock);
        if(consumeQuota){
            try {
                quota.consumeCvAnalysis(tenantId,analysisId);
            } catch (HiringQuotaApi.HiringQuotaExceededException exhausted) {
                analysis.setStatus(CvAnalysisRecordStatus.SKIPPED_QUOTA);
                analysis.setFailureCode("CV_ANALYSIS_QUOTA_EXCEEDED");
                analysis.setCompletedAt(now);
                analyses.saveAndFlush(analysis);
                if(refresh)application.setPendingCvAnalysisId(analysisId);
                else{application.setCvAnalysisStatus(CvAnalysisStatus.SKIPPED_QUOTA);application.setActiveCvAnalysisId(analysisId);}
                applications.save(application);
                return analysisId;
            }
        }
        ResumeAnalysisRequestedEvent request=request(application,cv,analysis,now);
        byte[] payload=write(request);
        analysis.setStatus(CvAnalysisRecordStatus.QUEUED);
        analysis.setRequestEventId(request.eventId());
        analysis.setRequestPayload(new String(payload,StandardCharsets.UTF_8));
        analysis.setRequestPayloadSha256(sha256(payload));
        analysis.setNextPublishAt(now);
        analyses.saveAndFlush(analysis);
        if(refresh)application.setPendingCvAnalysisId(analysisId);
        else{application.setCvAnalysisStatus(CvAnalysisStatus.PENDING);application.setActiveCvAnalysisId(analysisId);}
        applications.save(application);
        return analysisId;
    }

    @Transactional
    public void cancel(UUID tenantId,UUID applicationId) {
        applications.findForUpdate(tenantId,applicationId).ifPresent(application->{
            if(application.getActiveCvAnalysisId()!=null){
                application.setCvAnalysisStatus(CvAnalysisStatus.CANCELLED);
                application.setActiveCvAnalysisId(null);
            }
            application.setPendingCvAnalysisId(null);applications.saveAndFlush(application);
        });
        analyses.cancelApplication(tenantId,applicationId);
        analyses.deleteApplication(tenantId,applicationId);
    }

    private UUID analysisId(RecruitmentApplication application,RecruitmentApplicationCv cv,
            String policyVersion,String modelVersion,int revision){
        return InterviewEventIdentity.resumeAnalysisId(application.getTenantId(),application.getId(),cv.getId(),
                cv.getContentSha256(),application.getCvAiModeSnapshot().name(),policyVersion,modelVersion,revision);
    }

    private RecruitmentCvAnalysis base(RecruitmentApplication application,RecruitmentApplicationCv cv,UUID id,
            String policyVersion,String modelVersion,int revision,UUID refreshRequestId){
        RecruitmentCvAnalysis value=new RecruitmentCvAnalysis();value.setId(id);value.setTenantId(application.getTenantId());
        value.setApplicationId(application.getId());value.setCvId(cv.getId());value.setCvSha256(cv.getContentSha256());
        value.setAnalysisMode(application.getCvAiModeSnapshot());value.setPolicyVersion(policyVersion);
        value.setModelVersion(modelVersion);value.setContractVersion("1.2");value.setAnalysisRevision(revision);
        value.setRefreshRequestId(refreshRequestId);return value;
    }

    private void activate(RecruitmentApplication application,RecruitmentCvAnalysis analysis,boolean refresh){
        if(refresh){application.setPendingCvAnalysisId(analysis.getId());applications.save(application);return;}
        application.setActiveCvAnalysisId(analysis.getId());
        application.setCvAnalysisStatus(switch(analysis.getStatus()){
            case QUEUED,PUBLISHED->CvAnalysisStatus.PENDING;case COMPLETED->CvAnalysisStatus.COMPLETED;
            case FAILED->CvAnalysisStatus.FAILED;case SKIPPED_QUOTA->CvAnalysisStatus.SKIPPED_QUOTA;
            case CANCELLED->CvAnalysisStatus.CANCELLED;});applications.save(application);
    }

    private ResumeAnalysisRequestedEvent request(RecruitmentApplication application,RecruitmentApplicationCv cv,
            RecruitmentCvAnalysis analysis,LocalDateTime now){
        UUID analysisId=analysis.getId();
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
        int limit=analysis.getAnalysisMode()==CvAiMode.PERSONALIZED_QUESTIONS
                ?properties.maxPersonalizedQuestions():0;
        JobContext context=jobContext(job);
        return new ResumeAnalysisRequestedEvent("1.2",
                InterviewEventIdentity.eventId("interview.resume-analysis.requested",analysisId,
                        "requested:v1.2:revision:"+analysis.getAnalysisRevision()),
                "interview.resume-analysis.requested",now.toInstant(ZoneOffset.UTC),application.getTenantId(),analysisId,
                analysisId,application.getId(),cv.getId(),cv.getPromotedObjectKey(),cv.getOriginalFilename(),
                cv.getContentType(),cv.getByteSize(),application.getLocale(),cv.getContentSha256(),
                analysis.getAnalysisMode().name(),analysis.getPolicyVersion(),analysis.getModelVersion(),
                analysis.getAnalysisRevision(),bounded(job.getTitle(),200),bounded(job.getDescription(),4000),
                context.anchors(),context.truncated(),List.copyOf(sectionIds),List.copyOf(questions),limit);
    }

    private JobContext jobContext(RecruitmentJob job){
        List<ResumeAnalysisRequestedEvent.JobContextAnchor> anchors=new ArrayList<>();boolean truncated=false;
        truncated|=anchor(anchors,"job:title","title",job.getTitle(),2000);
        truncated|=anchor(anchors,"job:description","description",job.getDescription(),12000);
        truncated|=anchor(anchors,"job:department","department",job.getDepartment(),1000);
        truncated|=anchor(anchors,"job:location","location",job.getLocation(),1000);
        truncated|=anchor(anchors,"job:employment_type","employment_type",job.getEmploymentType()==null?null:job.getEmploymentType().name(),200);
        truncated|=anchor(anchors,"job:work_mode","work_mode",job.getWorkMode()==null?null:job.getWorkMode().name(),200);
        truncated|=anchor(anchors,"job:experience_level","experience_level",job.getExperienceLevel()==null?null:job.getExperienceLevel().name(),200);
        truncated|=anchor(anchors,"job:language","language",job.getLanguage(),100);
        try{
            int qi=0;for(JsonNode question:mapper.readTree(job.getScreeningConfig())){String qid=question.path("questionId").asText(Integer.toString(qi));
                truncated|=anchor(anchors,"screening:"+qid+":prompt","screening_prompt",question.path("prompt").asText(),1000);
                int oi=0;for(JsonNode option:question.path("options")){String oid=option.path("optionId").asText(Integer.toString(oi++));
                    truncated|=anchor(anchors,"screening:"+qid+":option:"+oid,"screening_option",option.path("label").asText(),500);}qi++;}
        }catch(Exception e){throw new IllegalStateException("Stored screening config is invalid",e);}
        return new JobContext(List.copyOf(anchors),truncated);
    }
    private boolean anchor(List<ResumeAnalysisRequestedEvent.JobContextAnchor> anchors,String id,String field,String raw,int max){
        if(raw==null||raw.isBlank())return false;String value=raw.strip();boolean truncated=value.length()>max;
        anchors.add(new ResumeAnalysisRequestedEvent.JobContextAnchor(id,field,bounded(value,max)));return truncated;
    }
    private record JobContext(List<ResumeAnalysisRequestedEvent.JobContextAnchor> anchors,boolean truncated){}

    private byte[] write(Object value){try{return mapper.writeValueAsBytes(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private static String bounded(String value,int max){if(value==null)return "";String v=value.strip();return v.length()<=max?v:v.substring(0,max);}
    static String sha256(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
}
