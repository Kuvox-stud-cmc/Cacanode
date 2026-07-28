package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import com.cacanode.api.recruitment.model.RecruitmentEnums.AvailabilityExceptionKind;
import com.cacanode.api.recruitment.repository.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class InterviewInvitationServiceTest {
    @Test
    void refusesToIssueInvitationWhenInterviewAvailabilityIsNotConfigured() {
        UUID tenantId=UUID.randomUUID(),applicationId=UUID.randomUUID();
        RecruitmentApplicationRepository applications=mock(RecruitmentApplicationRepository.class);
        RecruitmentInterviewRepository interviews=mock(RecruitmentInterviewRepository.class);
        RecruitmentJobRepository jobs=mock(RecruitmentJobRepository.class);
        RecruitmentTenantSettingsRepository settings=mock(RecruitmentTenantSettingsRepository.class);
        RecruitmentAvailabilityWindowRepository windows=mock(RecruitmentAvailabilityWindowRepository.class);
        RecruitmentAvailabilityExceptionRepository exceptions=mock(RecruitmentAvailabilityExceptionRepository.class);
        RecruitmentCandidateEmailDeliveryRepository deliveries=mock(RecruitmentCandidateEmailDeliveryRepository.class);
        Clock clock=Clock.fixed(Instant.parse("2026-07-26T03:00:00Z"),ZoneOffset.UTC);
        RecruitmentProperties properties=new RecruitmentProperties(true,false,false,true,false,false,false,false);
        when(settings.findById(tenantId)).thenReturn(Optional.empty());

        var service=new InterviewInvitationService(
                applications,interviews,jobs,settings,windows,exceptions,deliveries,clock,properties);

        ConflictException error=assertThrows(ConflictException.class,()->service.invite(tenantId,applicationId,true));

        assertEquals("INTERVIEW_AVAILABILITY_NOT_CONFIGURED",error.getMessage());
        verify(windows).existsByTenantId(tenantId);
        verify(exceptions).existsByTenantIdAndKindAndExceptionDateGreaterThanEqual(
                tenantId,AvailabilityExceptionKind.EXTRA,LocalDate.of(2026,7,26));
        verifyNoInteractions(applications,interviews,jobs,deliveries);
    }
}
