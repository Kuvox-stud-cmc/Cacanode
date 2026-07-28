package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.recruitment.api.event.ResumeAnalysisOutcomeEvent;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class RecruitmentCvAnalysisQueryService {
    private final RecruitmentApplicationRepository applications;private final RecruitmentCvAnalysisRepository analyses;
    private final RecruitmentApplicationCvRepository cvs;
    private final ObjectMapper mapper;
    @Autowired(required=false) private com.cacanode.api.recruitment.service.RecruitmentCapabilityService capabilities;
    @Transactional(readOnly=true)
    public RecruitmentDtos.CvAnalysisResponse get(UUID tenantId,UUID applicationId){
        var application=applications.findByIdAndTenantId(applicationId,tenantId)
                .orElseThrow(()->new ResourceNotFoundException("Application was not found"));
        var analysis=application.getActiveCvAnalysisId()==null?null:
                analyses.findByIdAndTenantId(application.getActiveCvAnalysisId(),tenantId).orElse(null);
        var refresh=application.getPendingCvAnalysisId()==null?null:
                analyses.findByIdAndTenantId(application.getPendingCvAnalysisId(),tenantId).orElse(null);
        if(analysis==null)return new RecruitmentDtos.CvAnalysisResponse(application.getCvAiModeSnapshot(),
                application.getCvAnalysisStatus(),application.getCvAiPolicyVersion(),application.getCvAiModelVersion(),
                null,null,List.of(),List.of(),List.of(),null,true,null,null,null,List.of(),List.of(),null,
                false,refreshStatus(refresh),refresh==null?null:refresh.getFailureCode());
        List<ResumeAnalysisOutcomeEvent.Evidence> evidence=read(analysis.getEvidence(),new TypeReference<>(){});
        List<ResumeAnalysisOutcomeEvent.Skill> skills=read(analysis.getSkills(),new TypeReference<>(){});
        List<ResumeAnalysisOutcomeEvent.PersonalizedQuestion> questions=read(analysis.getPersonalizedQuestions(),new TypeReference<>(){});
        List<ResumeAnalysisOutcomeEvent.FitFinding> strengths=read(analysis.getStrengths(),new TypeReference<>(){});
        List<ResumeAnalysisOutcomeEvent.FitFinding> gaps=read(analysis.getGaps(),new TypeReference<>(){});
        boolean pending=refresh!=null&&(refresh.getStatus()==CvAnalysisRecordStatus.QUEUED||refresh.getStatus()==CvAnalysisRecordStatus.PUBLISHED);
        boolean terminal=Set.of(ApplicationStatus.INTERVIEW_COMPLETED,ApplicationStatus.SHORTLISTED,
                ApplicationStatus.REJECTED,ApplicationStatus.WITHDRAWN).contains(application.getStatus());
        boolean capability=capabilities==null||capabilities.capabilities(tenantId).cvAiEnabled();
        boolean promoted=cvs.findByTenantIdAndApplicationIdAndActiveTrue(tenantId,applicationId)
                .filter(cv->cv.getStorageState()==CvStorageState.PROMOTED&&cv.getPromotedObjectKey()!=null).isPresent();
        boolean refreshAvailable=capability&&!pending&&!terminal&&application.isCvPresent()&&promoted&&application.getCvAiConsentAt()!=null
                &&application.getCvAiModeSnapshot()!=CvAiMode.OFF
                &&Set.of(CvAnalysisRecordStatus.COMPLETED,CvAnalysisRecordStatus.FAILED).contains(analysis.getStatus());
        return new RecruitmentDtos.CvAnalysisResponse(analysis.getAnalysisMode(),application.getCvAnalysisStatus(),
                analysis.getPolicyVersion(),analysis.getModelVersion(),analysis.getCompletedAt(),analysis.getSummary(),
                evidence.stream().map(e->new RecruitmentDtos.CvAnalysisEvidence(e.anchorId(),e.excerpt(),e.sourceLocation())).toList(),
                skills.stream().map(s->new RecruitmentDtos.CvAnalysisSkill(s.name(),s.evidenceAnchorIds())).toList(),
                questions.stream().map(q->new RecruitmentDtos.CvAnalysisQuestion(q.questionId(),q.targetSectionId(),
                        q.prompt(),q.competency(),q.rubric(),q.evidenceAnchorIds())).toList(),analysis.getFailureCode(),true,
                analysis.getFitScorePercent(),analysis.getFitConfidence(),analysis.getFitExplanation(),
                findings(strengths),findings(gaps),analysis.getAnalysisRevision(),refreshAvailable,refreshStatus(refresh),
                refresh==null?null:refresh.getFailureCode());
    }
    private List<RecruitmentDtos.CvAnalysisFitFinding> findings(List<ResumeAnalysisOutcomeEvent.FitFinding> values){return values.stream()
            .map(v->new RecruitmentDtos.CvAnalysisFitFinding(v.weightPercent(),v.matchPercent(),v.evidenceStatus(),
                    v.explanation(),v.jobExcerpt(),v.jobAnchorId(),v.cvEvidenceAnchorIds())).toList();}
    private String refreshStatus(com.cacanode.api.recruitment.model.RecruitmentCvAnalysis value){if(value==null)return "NOT_REQUESTED";
        return switch(value.getStatus()){case QUEUED,PUBLISHED->"PENDING";case SKIPPED_QUOTA->"QUOTA_EXHAUSTED";
            case COMPLETED->"COMPLETED";case FAILED->"FAILED";case CANCELLED->"CANCELLED";};}
    private <T> T read(String json,TypeReference<T> type){try{return mapper.readValue(json,type);}catch(Exception e){throw new IllegalStateException("Stored CV analysis is invalid",e);}}
}
