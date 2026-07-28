package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.recruitment.api.InterviewEventIdentity;
import com.cacanode.api.recruitment.config.CvAnalysisProperties;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentCvAnalysisServiceTest {
    private final RecruitmentApplicationRepository applications=mock(RecruitmentApplicationRepository.class);
    private final RecruitmentApplicationCvRepository cvs=mock(RecruitmentApplicationCvRepository.class);
    private final RecruitmentCvAnalysisRepository analyses=mock(RecruitmentCvAnalysisRepository.class);
    private final RecruitmentJobRepository jobs=mock(RecruitmentJobRepository.class);
    private final HiringQuotaApi quota=mock(HiringQuotaApi.class);
    private final CvAnalysisProperties properties=new CvAnalysisProperties("cv-redaction-v1","resume-analysis-v1",
            50000,250,2,3,10,1000,2,300);
    private final Clock clock=Clock.fixed(Instant.parse("2026-07-24T03:00:00Z"),ZoneOffset.UTC);

    @Test void consumesQuotaQueuesDeterministicV12RequestAndIsIdempotent(){
        Fixture f=fixture();service().process(f.application.getTenantId(),f.application.getId());
        UUID expected=InterviewEventIdentity.resumeAnalysisId(f.application.getTenantId(),f.application.getId(),
                f.cv.getId(),f.cv.getContentSha256(),"PERSONALIZED_QUESTIONS",
                "cv-redaction-v1","resume-analysis-v1",1);
        assertEquals(expected,f.application.getActiveCvAnalysisId());assertEquals(CvAnalysisStatus.PENDING,f.application.getCvAnalysisStatus());
        ArgumentCaptor<RecruitmentCvAnalysis> saved=ArgumentCaptor.forClass(RecruitmentCvAnalysis.class);
        verify(analyses).saveAndFlush(saved.capture());assertEquals(CvAnalysisRecordStatus.QUEUED,saved.getValue().getStatus());
        assertNull(saved.getValue().getVersion());
        assertTrue(saved.getValue().getRequestPayload().contains("\"schema_version\":\"1.2\""));
        assertTrue(saved.getValue().getRequestPayload().contains("\"analysis_revision\":1"));
        assertTrue(saved.getValue().getRequestPayload().contains("\"job_context_anchors\""));
        assertTrue(saved.getValue().getRequestPayload().contains("\"personalized_question_limit\":2"));
        verify(quota).consumeCvAnalysis(f.application.getTenantId(),expected);
        service().process(f.application.getTenantId(),f.application.getId());verifyNoMoreInteractions(quota);
    }

    @Test void quotaExhaustionRecordsStableTemplateOnlyFallback(){
        Fixture f=fixture();doThrow(new HiringQuotaApi.HiringQuotaExceededException(
                "CV_ANALYSIS_QUOTA_EXCEEDED","exhausted")).when(quota).consumeCvAnalysis(any(),any());
        service().process(f.application.getTenantId(),f.application.getId());
        ArgumentCaptor<RecruitmentCvAnalysis> saved=ArgumentCaptor.forClass(RecruitmentCvAnalysis.class);
        verify(analyses).saveAndFlush(saved.capture());assertEquals(CvAnalysisRecordStatus.SKIPPED_QUOTA,saved.getValue().getStatus());
        assertEquals("CV_ANALYSIS_QUOTA_EXCEEDED",saved.getValue().getFailureCode());
        assertEquals(CvAnalysisStatus.SKIPPED_QUOTA,f.application.getCvAnalysisStatus());
        assertNull(saved.getValue().getRequestPayload());
    }

    @Test void retriesLegacyModelFailureWithCurrentIdentityWithoutChargingQuotaAgain(){
        Fixture f=fixture();UUID legacy=InterviewEventIdentity.resumeAnalysisId(
                f.application.getTenantId(),f.application.getId(),f.cv.getContentSha256(),
                "PERSONALIZED_QUESTIONS","cv-redaction-v1","resume-analysis-v1");
        RecruitmentCvAnalysis failed=new RecruitmentCvAnalysis();failed.setId(legacy);
        failed.setTenantId(f.application.getTenantId());failed.setApplicationId(f.application.getId());
        failed.setCvId(f.cv.getId());failed.setCvSha256(f.cv.getContentSha256());
        failed.setAnalysisMode(CvAiMode.PERSONALIZED_QUESTIONS);failed.setPolicyVersion("cv-redaction-v1");
        failed.setModelVersion("resume-analysis-v1");failed.setStatus(CvAnalysisRecordStatus.FAILED);
        failed.setFailureCode("CV_ANALYSIS_INVALID_MODEL_OUTPUT");
        f.application.setActiveCvAnalysisId(legacy);f.application.setCvAnalysisStatus(CvAnalysisStatus.FAILED);
        when(analyses.findForUpdate(f.application.getTenantId(),legacy)).thenReturn(Optional.of(failed));

        service().processRetryableLegacyFailure(f.application.getTenantId(),f.application.getId());

        String retryModelVersion="resume-analysis-v1+pipeline-v2";
        UUID current=InterviewEventIdentity.resumeAnalysisId(f.application.getTenantId(),f.application.getId(),
                f.cv.getId(),f.cv.getContentSha256(),"PERSONALIZED_QUESTIONS",
                "cv-redaction-v1",retryModelVersion,2);
        assertEquals(current,f.application.getActiveCvAnalysisId());
        assertEquals(CvAnalysisStatus.PENDING,f.application.getCvAnalysisStatus());
        ArgumentCaptor<RecruitmentCvAnalysis> saved=ArgumentCaptor.forClass(RecruitmentCvAnalysis.class);
        verify(analyses).saveAndFlush(saved.capture());
        assertEquals(current,saved.getValue().getId());
        assertEquals(retryModelVersion,saved.getValue().getModelVersion());
        verifyNoInteractions(quota);
    }

    @Test void refreshPreservesCurrentReviewAndChargesOneUnitForIdempotentReplay(){
        Fixture f=fixture();UUID currentId=UUID.randomUUID(),requestId=UUID.randomUUID();
        RecruitmentCvAnalysis current=new RecruitmentCvAnalysis();current.setId(currentId);
        current.setTenantId(f.application.getTenantId());current.setApplicationId(f.application.getId());
        current.setStatus(CvAnalysisRecordStatus.COMPLETED);current.setAnalysisRevision(1);
        f.application.setActiveCvAnalysisId(currentId);f.application.setCvAnalysisStatus(CvAnalysisStatus.COMPLETED);
        when(analyses.findForUpdate(f.application.getTenantId(),currentId)).thenReturn(Optional.of(current));
        when(analyses.findByTenantIdAndApplicationIdAndRefreshRequestId(
                f.application.getTenantId(),f.application.getId(),requestId)).thenReturn(Optional.empty());
        when(analyses.maxRevision(f.application.getTenantId(),f.application.getId())).thenReturn(1);

        UUID refreshId=service().refresh(f.application.getTenantId(),f.application.getId(),requestId);

        assertEquals(currentId,f.application.getActiveCvAnalysisId());assertEquals(refreshId,f.application.getPendingCvAnalysisId());
        ArgumentCaptor<RecruitmentCvAnalysis> saved=ArgumentCaptor.forClass(RecruitmentCvAnalysis.class);
        verify(analyses).saveAndFlush(saved.capture());assertEquals(2,saved.getValue().getAnalysisRevision());
        assertEquals(requestId,saved.getValue().getRefreshRequestId());verify(quota).consumeCvAnalysis(f.application.getTenantId(),refreshId);
        when(analyses.findByTenantIdAndApplicationIdAndRefreshRequestId(
                f.application.getTenantId(),f.application.getId(),requestId)).thenReturn(Optional.of(saved.getValue()));
        assertEquals(refreshId,service().refresh(f.application.getTenantId(),f.application.getId(),requestId));
        verifyNoMoreInteractions(quota);
    }

    private RecruitmentCvAnalysisService service(){return new RecruitmentCvAnalysisService(applications,cvs,analyses,jobs,quota,properties,JsonMapper.builder().findAndAddModules().build(),clock);}
    private Fixture fixture(){
        UUID tenant=UUID.randomUUID(),applicationId=UUID.randomUUID(),jobId=UUID.randomUUID(),cvId=UUID.randomUUID();
        UUID sectionId=UUID.randomUUID(),questionId=UUID.randomUUID();
        RecruitmentApplication application=new RecruitmentApplication();application.setId(applicationId);application.setTenantId(tenant);
        application.setJobId(jobId);application.setStatus(ApplicationStatus.SUBMITTED);application.setLocale("en-US");
        application.setCvAiModeSnapshot(CvAiMode.PERSONALIZED_QUESTIONS);application.setCvAiConsentAt(LocalDateTime.now(clock));
        application.setCvAiPolicyVersion("cv-redaction-v1");application.setCvAiModelVersion("resume-analysis-v1");
        application.setTemplateSnapshot("{\"sections\":[{\"sectionId\":\""+sectionId+"\",\"kind\":\"CORE\",\"questions\":[{\"questionId\":\""+questionId+"\",\"prompt\":\"Tell me about reliability\",\"competency\":\"Reliability\"}]}]}");
        RecruitmentApplicationCv cv=new RecruitmentApplicationCv();cv.setId(cvId);cv.setTenantId(tenant);cv.setApplicationId(applicationId);
        cv.setStorageState(CvStorageState.PROMOTED);cv.setPromotedObjectKey("recruitment/"+tenant+"/applications/"+applicationId+"/cv/"+cvId);
        cv.setContentSha256("a".repeat(64));cv.setOriginalFilename("cv.pdf");cv.setContentType("application/pdf");cv.setByteSize(100);
        RecruitmentJob job=new RecruitmentJob();job.setId(jobId);job.setTenantId(tenant);job.setTitle("Engineer");job.setDescription("Build reliable systems");
        when(applications.findForUpdate(tenant,applicationId)).thenReturn(Optional.of(application));
        when(cvs.findActiveForUpdate(tenant,applicationId)).thenReturn(Optional.of(cv));
        when(analyses.findByIdAndTenantId(any(),eq(tenant))).thenReturn(Optional.empty());
        when(jobs.findByIdAndTenantId(jobId,tenant)).thenReturn(Optional.of(job));
        return new Fixture(application,cv);
    }
    private record Fixture(RecruitmentApplication application,RecruitmentApplicationCv cv){}
}
