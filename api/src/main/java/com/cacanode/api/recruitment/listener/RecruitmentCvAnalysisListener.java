package com.cacanode.api.recruitment.listener;

import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import com.cacanode.api.recruitment.api.event.RecruitmentApplicationSubmittedEvent;
import com.cacanode.api.recruitment.service.RecruitmentCvAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.messaging-enabled:false} and ${app.recruitment.cv-ai-enabled:false}")
public class RecruitmentCvAnalysisListener {
    private final ModuleEventInboxService inbox;private final RecruitmentCvAnalysisService service;
    @EventListener @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void submitted(RecruitmentApplicationSubmittedEvent event){
        if(!inbox.claim("recruitment.cv-analysis"))return;service.process(event.tenantId(),event.applicationId());
    }
}
