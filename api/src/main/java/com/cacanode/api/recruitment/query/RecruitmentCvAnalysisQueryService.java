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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service @RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class RecruitmentCvAnalysisQueryService {
    private final RecruitmentApplicationRepository applications;private final RecruitmentCvAnalysisRepository analyses;
    private final ObjectMapper mapper;
    @Transactional(readOnly=true)
    public RecruitmentDtos.CvAnalysisResponse get(UUID tenantId,UUID applicationId){
        var application=applications.findByIdAndTenantId(applicationId,tenantId)
                .orElseThrow(()->new ResourceNotFoundException("Application was not found"));
        var analysis=application.getActiveCvAnalysisId()==null?null:
                analyses.findByIdAndTenantId(application.getActiveCvAnalysisId(),tenantId).orElse(null);
        if(analysis==null)return new RecruitmentDtos.CvAnalysisResponse(application.getCvAiModeSnapshot(),
                application.getCvAnalysisStatus(),application.getCvAiPolicyVersion(),application.getCvAiModelVersion(),
                null,null,List.of(),List.of(),List.of(),null,true);
        List<ResumeAnalysisOutcomeEvent.Evidence> evidence=read(analysis.getEvidence(),new TypeReference<>(){});
        List<ResumeAnalysisOutcomeEvent.Skill> skills=read(analysis.getSkills(),new TypeReference<>(){});
        List<ResumeAnalysisOutcomeEvent.PersonalizedQuestion> questions=read(analysis.getPersonalizedQuestions(),new TypeReference<>(){});
        return new RecruitmentDtos.CvAnalysisResponse(analysis.getAnalysisMode(),application.getCvAnalysisStatus(),
                analysis.getPolicyVersion(),analysis.getModelVersion(),analysis.getCompletedAt(),analysis.getSummary(),
                evidence.stream().map(e->new RecruitmentDtos.CvAnalysisEvidence(e.anchorId(),e.excerpt(),e.sourceLocation())).toList(),
                skills.stream().map(s->new RecruitmentDtos.CvAnalysisSkill(s.name(),s.evidenceAnchorIds())).toList(),
                questions.stream().map(q->new RecruitmentDtos.CvAnalysisQuestion(q.questionId(),q.targetSectionId(),
                        q.prompt(),q.competency(),q.rubric(),q.evidenceAnchorIds())).toList(),analysis.getFailureCode(),true);
    }
    private <T> T read(String json,TypeReference<T> type){try{return mapper.readValue(json,type);}catch(Exception e){throw new IllegalStateException("Stored CV analysis is invalid",e);}}
}
