package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.common.event.durable.DurableEventPublisher;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.api.event.RecruitmentApplicationSubmittedEvent;
import com.cacanode.api.recruitment.model.RecruitmentApplication;
import com.cacanode.api.recruitment.model.RecruitmentEnums.ApplicationStatus;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class ApplicationSubmissionTransitionService {
    private final HiringQuotaApi quota;
    private final RecruitmentApplicationRepository applications;
    private final DurableEventPublisher events;
    private final Clock clock;
    @Autowired(required=false) private RecruitmentProjectionEventPublisher projectionEvents;

    public boolean verifyLocked(RecruitmentApplication application) {
        if (application.getStatus()!=ApplicationStatus.SUBMITTED_UNVERIFIED) return false;
        try { quota.consumeVerifiedApplication(application.getTenantId(),application.getId()); }
        catch (HiringQuotaApi.HiringQuotaException e) { throw new ConflictException(e.getMessage()); }
        LocalDateTime now=LocalDateTime.now(clock);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setVerifiedAt(now);
        applications.saveAndFlush(application);
        UUID eventId=UUID.randomUUID();
        events.publish("recruitment.application.submitted.v1",1,new RecruitmentApplicationSubmittedEvent(
                eventId,application.getId(),application.getJobId(),application.getTenantId(),now));
        if(projectionEvents!=null)projectionEvents.application(application,"application.submitted");
        return true;
    }
}
