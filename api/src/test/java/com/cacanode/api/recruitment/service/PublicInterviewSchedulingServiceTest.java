package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.model.RecruitmentInterview;
import com.cacanode.api.recruitment.model.RecruitmentInterviewInvitationToken;
import com.cacanode.api.recruitment.model.RecruitmentEnums.AvailabilityExceptionKind;
import com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus;
import com.cacanode.api.recruitment.query.RecruitmentInvitationQueryService;
import com.cacanode.api.recruitment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicInterviewSchedulingServiceTest {
    @Test void omitsDstGapsAndReturnsBothOverlapInstants(){
        ZoneId berlin=ZoneId.of("Europe/Berlin");
        assertTrue(PublicInterviewSchedulingService.resolveLocal(LocalDateTime.of(2026,3,29,2,30),berlin).isEmpty());
        var overlap=PublicInterviewSchedulingService.resolveLocal(LocalDateTime.of(2026,10,25,2,30),berlin);
        assertEquals(2,overlap.size());assertNotEquals(overlap.get(0),overlap.get(1));
    }

    @Test void ordinaryZonesResolveOneInstant(){
        assertEquals(List.of(Instant.parse("2026-07-24T02:00:00Z")),PublicInterviewSchedulingService.resolveLocal(
                LocalDateTime.of(2026,7,24,9,0),ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Test void reportsWhenAvailabilityWasRemovedAfterInvitationWasIssued(){
        UUID tenantId=UUID.randomUUID(),interviewId=UUID.randomUUID(),applicationId=UUID.randomUUID();
        RecruitmentInterviewInvitationTokenRepository tokens=mock(RecruitmentInterviewInvitationTokenRepository.class);
        RecruitmentInterviewRepository interviews=mock(RecruitmentInterviewRepository.class);
        RecruitmentApplicationRepository applications=mock(RecruitmentApplicationRepository.class);
        RecruitmentTenantSettingsRepository settings=mock(RecruitmentTenantSettingsRepository.class);
        RecruitmentAvailabilityWindowRepository windows=mock(RecruitmentAvailabilityWindowRepository.class);
        RecruitmentAvailabilityExceptionRepository exceptions=mock(RecruitmentAvailabilityExceptionRepository.class);
        RecruitmentCandidateEmailDeliveryRepository deliveries=mock(RecruitmentCandidateEmailDeliveryRepository.class);
        InterviewInvitationService invitations=mock(InterviewInvitationService.class);
        HiringQuotaApi quota=mock(HiringQuotaApi.class);RecruitmentTokenSupport tokenSupport=mock(RecruitmentTokenSupport.class);
        RecruitmentInvitationQueryService queries=mock(RecruitmentInvitationQueryService.class);
        RecruitmentInterviewCancellationService cancellations=mock(RecruitmentInterviewCancellationService.class);
        PublicRecruitmentProperties properties=new PublicRecruitmentProperties(
                null,null,null,false,false,null,null,false,null,0,0);
        Clock clock=Clock.fixed(Instant.parse("2026-07-26T03:00:00Z"),ZoneOffset.UTC);
        RecruitmentInterviewInvitationToken token=new RecruitmentInterviewInvitationToken();token.setTenantId(tenantId);
        token.setInterviewId(interviewId);token.setApplicationId(applicationId);token.setExpiresAt(LocalDateTime.of(2026,8,2,3,0));
        RecruitmentInterview interview=new RecruitmentInterview();interview.setId(interviewId);interview.setTenantId(tenantId);
        interview.setApplicationId(applicationId);interview.setStatus(InterviewStatus.INVITED);
        interview.setInvitationExpiresAt(LocalDateTime.of(2026,8,2,3,0));
        when(tokenSupport.hash("raw-token")).thenReturn("hash");when(tokens.findForUpdateByHash("hash")).thenReturn(Optional.of(token));
        when(interviews.findByIdAndTenantId(interviewId,tenantId)).thenReturn(Optional.of(interview));
        when(settings.findById(tenantId)).thenReturn(Optional.empty());
        var service=new PublicInterviewSchedulingService(tokens,interviews,applications,settings,windows,exceptions,
                deliveries,invitations,quota,tokenSupport,queries,new ObjectMapper(),clock,cancellations,properties);

        ConflictException error=assertThrows(ConflictException.class,()->service.slots("raw-token",null,14));

        assertEquals("INTERVIEW_AVAILABILITY_NOT_CONFIGURED",error.getMessage());
        verify(windows).existsByTenantId(tenantId);
        verify(exceptions).existsByTenantIdAndKindAndExceptionDateGreaterThanEqual(
                tenantId,AvailabilityExceptionKind.EXTRA,LocalDate.of(2026,7,26));
    }
}
