package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.api.ResumeAnalysisPublisher;
import com.cacanode.api.recruitment.api.event.ResumeAnalysisRequestedEvent;
import com.cacanode.api.recruitment.config.CvAnalysisProperties;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.messaging-enabled:false} and ${app.recruitment.cv-ai-enabled:false}")
public class RecruitmentCvAnalysisPublisherWorker {
    private final RecruitmentCvAnalysisRepository analyses;private final RecruitmentApplicationRepository applications;
    private final ResumeAnalysisPublisher publisher;private final ObjectMapper mapper;
    private final CvAnalysisProperties properties;private final Clock clock;

    @Scheduled(fixedDelayString="${app.recruitment.cv-analysis.publisher-delay-ms:1000}") @Transactional
    public void publishDue(){for(var analysis:analyses.lockDuePublication())publish(analysis);}

    private void publish(com.cacanode.api.recruitment.model.RecruitmentCvAnalysis analysis){
        try{
            publisher.publish(mapper.readValue(analysis.getRequestPayload(),ResumeAnalysisRequestedEvent.class));
            analysis.setStatus(CvAnalysisRecordStatus.PUBLISHED);analysis.setPublishedAt(now());
            analysis.setNextPublishAt(null);analyses.save(analysis);
        }catch(Exception exception){
            int attempts=analysis.getPublishAttempts()+1;analysis.setPublishAttempts(attempts);
            if(attempts>=properties.maxPublicationAttempts()){
                analysis.setStatus(CvAnalysisRecordStatus.FAILED);analysis.setFailureCode("CV_ANALYSIS_PUBLICATION_FAILED");
                analysis.setCompletedAt(now());analysis.setNextPublishAt(null);
                applications.findForUpdate(analysis.getTenantId(),analysis.getApplicationId()).ifPresent(application->{
                    if(analysis.getId().equals(application.getActiveCvAnalysisId())){
                        application.setCvAnalysisStatus(CvAnalysisStatus.FAILED);applications.save(application);
                    }
                });
            }else{
                long delay=Math.min(properties.maxBackoffSeconds(),properties.initialBackoffSeconds() << Math.min(attempts-1,20));
                analysis.setNextPublishAt(now().plusSeconds(delay));
            }
            analyses.save(analysis);log.warn("CV analysis publication failed analysisId={} attempt={}",analysis.getId(),attempts);
        }
    }
    private LocalDateTime now(){return LocalDateTime.now(clock);}
}
