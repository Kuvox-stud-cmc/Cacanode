package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.common.event.durable.DurableEventPublisher;
import com.cacanode.api.recruitment.model.RecruitmentApplication;
import com.cacanode.api.recruitment.model.RecruitmentEnums.ApplicationStatus;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApplicationSubmissionTransitionServiceTest {
    @Test void quotaAndDurableEventAreAppliedExactlyOnce(){
        HiringQuotaApi quota=mock(HiringQuotaApi.class);RecruitmentApplicationRepository applications=mock(RecruitmentApplicationRepository.class);
        DurableEventPublisher events=mock(DurableEventPublisher.class);Clock clock=Clock.fixed(Instant.parse("2026-07-24T03:00:00Z"),ZoneOffset.UTC);
        var service=new ApplicationSubmissionTransitionService(quota,applications,events,clock);
        RecruitmentApplication application=new RecruitmentApplication();application.setId(UUID.randomUUID());application.setTenantId(UUID.randomUUID());
        application.setJobId(UUID.randomUUID());application.setStatus(ApplicationStatus.SUBMITTED_UNVERIFIED);
        assertTrue(service.verifyLocked(application));assertFalse(service.verifyLocked(application));
        assertEquals(ApplicationStatus.SUBMITTED,application.getStatus());assertEquals(LocalDateTime.of(2026,7,24,3,0),application.getVerifiedAt());
        verify(quota).consumeVerifiedApplication(application.getTenantId(),application.getId());
        verify(events).publish(eq("recruitment.application.submitted.v1"),eq(1),any());
        verify(applications).saveAndFlush(application);
    }
}
