package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.config.CvAnalysisProperties;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.nio.file.*;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentCvAnalysisOutcomeServiceTest {
    @Test void appliesExactOutcomeOnceAndRejectsConflictingReplay() throws Exception {
        ObjectMapper mapper=JsonMapper.builder().findAndAddModules().build();byte[] payload=Files.readAllBytes(
                Path.of("../contracts/ai-interview/v1/resume-analysis-outcome-v1.1.fixture.json"));
        var event=mapper.readTree(payload);UUID tenant=UUID.fromString(event.get("tenant_id").asText());
        UUID analysisId=UUID.fromString(event.get("analysis_id").asText());UUID applicationId=UUID.fromString(event.get("application_id").asText());
        RecruitmentCvAnalysis analysis=new RecruitmentCvAnalysis();analysis.setId(analysisId);analysis.setTenantId(tenant);
        analysis.setApplicationId(applicationId);analysis.setCvSha256(event.get("cv_sha256").asText());
        analysis.setAnalysisMode(CvAiMode.PERSONALIZED_QUESTIONS);analysis.setPolicyVersion("cv-redaction-v1");
        analysis.setModelVersion("resume-analysis-v1");analysis.setStatus(CvAnalysisRecordStatus.PUBLISHED);
        analysis.setRequestPayload("{\"allowed_core_section_ids\":[\"55555555-5555-4555-8555-555555555555\"]}");
        RecruitmentApplication application=new RecruitmentApplication();application.setId(applicationId);application.setTenantId(tenant);
        application.setActiveCvAnalysisId(analysisId);application.setCvAnalysisStatus(CvAnalysisStatus.PENDING);
        RecruitmentCvAnalysisRepository analyses=mock(RecruitmentCvAnalysisRepository.class);
        RecruitmentCvAnalysisInboxRepository inbox=mock(RecruitmentCvAnalysisInboxRepository.class);
        RecruitmentApplicationRepository applications=mock(RecruitmentApplicationRepository.class);
        when(analyses.findForUpdate(tenant,analysisId)).thenReturn(Optional.of(analysis));
        when(applications.findForUpdate(tenant,applicationId)).thenReturn(Optional.of(application));
        var properties=new CvAnalysisProperties("cv-redaction-v1","resume-analysis-v1",50000,250,2,3,10,1000,2,300);
        var service=new RecruitmentCvAnalysisOutcomeService(analyses,inbox,applications,mapper,properties,
                Clock.fixed(Instant.parse("2026-07-24T03:00:00Z"),ZoneOffset.UTC));
        service.accept(payload);assertEquals(CvAnalysisRecordStatus.COMPLETED,analysis.getStatus());
        assertEquals(CvAnalysisStatus.COMPLETED,application.getCvAnalysisStatus());assertFalse(analysis.getEvidence().contains("candidate@example.com"));
        ArgumentCaptor<RecruitmentCvAnalysisInbox> captured=ArgumentCaptor.forClass(RecruitmentCvAnalysisInbox.class);
        verify(inbox).save(captured.capture());when(inbox.findById(captured.getValue().getEventId())).thenReturn(Optional.of(captured.getValue()));
        service.accept(payload);verify(analyses,times(1)).save(analysis);
        byte[] conflicting=new String(payload).replace("Backend engineer with experience","Different summary with experience").getBytes();
        assertThrows(AmqpRejectAndDontRequeueException.class,()->service.accept(conflicting));
    }


    @Test void verifiesV12WeightedGroundingAndPromotesSuccessfulRefresh() throws Exception {
        ObjectMapper mapper=JsonMapper.builder().findAndAddModules().build();byte[] payload=Files.readAllBytes(
                Path.of("../contracts/ai-interview/v1/resume-analysis-outcome-v1.2.fixture.json"));
        var event=mapper.readTree(payload);UUID tenant=UUID.fromString(event.get("tenant_id").asText());
        UUID analysisId=UUID.fromString(event.get("analysis_id").asText());UUID applicationId=UUID.fromString(event.get("application_id").asText());
        RecruitmentCvAnalysis analysis=new RecruitmentCvAnalysis();analysis.setId(analysisId);analysis.setTenantId(tenant);
        analysis.setApplicationId(applicationId);analysis.setCvSha256(event.get("cv_sha256").asText());
        analysis.setAnalysisMode(CvAiMode.PERSONALIZED_QUESTIONS);analysis.setPolicyVersion("cv-redaction-fit-v2");
        analysis.setModelVersion("resume-analysis-v2");analysis.setContractVersion("1.2");analysis.setAnalysisRevision(2);
        analysis.setStatus(CvAnalysisRecordStatus.PUBLISHED);analysis.setRequestPayload("""
                {"allowed_core_section_ids":["55555555-5555-4555-8555-555555555555"],"job_context_anchors":[
                {"anchor_id":"job:description","excerpt":"Build reliable Java services."},
                {"anchor_id":"job:experience_level","excerpt":"MID"}]}
                """);
        UUID previous=UUID.randomUUID();RecruitmentApplication application=new RecruitmentApplication();
        application.setId(applicationId);application.setTenantId(tenant);application.setActiveCvAnalysisId(previous);
        application.setPendingCvAnalysisId(analysisId);application.setCvAnalysisStatus(CvAnalysisStatus.COMPLETED);
        RecruitmentCvAnalysisRepository analyses=mock(RecruitmentCvAnalysisRepository.class);
        RecruitmentCvAnalysisInboxRepository inbox=mock(RecruitmentCvAnalysisInboxRepository.class);
        RecruitmentApplicationRepository applications=mock(RecruitmentApplicationRepository.class);
        when(analyses.findForUpdate(tenant,analysisId)).thenReturn(Optional.of(analysis));
        when(applications.findForUpdate(tenant,applicationId)).thenReturn(Optional.of(application));
        var properties=new CvAnalysisProperties("cv-redaction-fit-v2","resume-analysis-v2",50000,250,2,3,10,1000,2,300);
        new RecruitmentCvAnalysisOutcomeService(analyses,inbox,applications,mapper,properties,
                Clock.fixed(Instant.parse("2026-07-27T08:01:00Z"),ZoneOffset.UTC)).accept(payload);
        assertEquals(55,analysis.getFitScorePercent());assertEquals("MEDIUM",analysis.getFitConfidence());
        assertEquals(analysisId,application.getActiveCvAnalysisId());assertNull(application.getPendingCvAnalysisId());
        assertEquals(CvAnalysisStatus.COMPLETED,application.getCvAnalysisStatus());
    }
}
