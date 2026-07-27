package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.messaging-enabled:false} and ${app.recruitment.cv-ai-enabled:false}")
public class RecruitmentCvAnalysisReconciler {
    private final RecruitmentApplicationRepository applications;private final RecruitmentCvAnalysisService service;
    @Scheduled(initialDelayString="${app.recruitment.cv-analysis.reconcile-initial-delay-ms:5000}",
            fixedDelayString="${app.recruitment.cv-analysis.reconcile-delay-ms:60000}") @Transactional
    public void reconcile(){reconcileCandidates();}
    private void reconcileCandidates(){
        for(var application:applications.lockCvAnalysisCandidates())
            service.process(application.getTenantId(),application.getId());
        for(var application:applications.lockRetryableLegacyCvAnalysisFailures())
            service.processRetryableLegacyFailure(application.getTenantId(),application.getId());
    }
}
