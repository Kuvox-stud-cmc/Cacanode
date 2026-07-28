package com.cacanode.api.recruitment.listener;

import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import com.cacanode.api.recruitment.api.event.RecruitmentApplicationSubmittedEvent;
import com.cacanode.api.recruitment.service.RecruitmentAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false} and ${app.recruitment.automation-enabled:false}")
public class RecruitmentAutomationListener {
    private final ModuleEventInboxService inbox;
    private final RecruitmentAutomationService automation;

    @EventListener
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void submitted(RecruitmentApplicationSubmittedEvent event){
        if(!inbox.claim("recruitment.application-automation"))return;
        automation.process(event.tenantId(),event.applicationId());
    }
}
