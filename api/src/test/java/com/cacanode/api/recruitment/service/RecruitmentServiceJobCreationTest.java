package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.BillingModuleApi;
import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CvPolicy;
import com.cacanode.api.recruitment.model.RecruitmentJob;
import com.cacanode.api.recruitment.query.RecruitmentQueryService;
import com.cacanode.api.recruitment.repository.InterviewTemplateRepository;
import com.cacanode.api.recruitment.repository.InterviewTemplateRevisionRepository;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import com.cacanode.api.recruitment.repository.RecruitmentCandidateRepository;
import com.cacanode.api.recruitment.repository.RecruitmentInterviewRepository;
import com.cacanode.api.recruitment.repository.RecruitmentJobRepository;
import com.cacanode.api.recruitment.repository.RecruitmentTenantSettingsRepository;
import com.cacanode.api.tenant.api.TenantPublicProfileApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecruitmentServiceJobCreationTest {
    @Test
    void flushesNewJobBeforePublishingItsProjectionEvent() {
        RecruitmentJobRepository jobs = mock(RecruitmentJobRepository.class);
        RecruitmentCapabilityService capabilities = mock(RecruitmentCapabilityService.class);
        ScreeningSupport screening = mock(ScreeningSupport.class);
        RecruitmentProjectionEventPublisher projections = mock(RecruitmentProjectionEventPublisher.class);
        LocalDateTime persistedAt = LocalDateTime.parse("2026-07-30T09:00:00");
        when(screening.validateAndWrite(any())).thenReturn("[]");
        when(screening.read("[]")).thenReturn(List.of());
        when(jobs.saveAndFlush(any(RecruitmentJob.class))).thenAnswer(invocation -> {
            RecruitmentJob job = invocation.getArgument(0);
            job.setId(UUID.randomUUID());
            job.setCreatedAt(persistedAt);
            job.setUpdatedAt(persistedAt);
            return job;
        });
        RecruitmentService service = service(jobs, capabilities, screening);
        ReflectionTestUtils.setField(service, "projectionEvents", projections);
        UUID tenantId = UUID.randomUUID();
        var request = new RecruitmentDtos.JobWrite(
                "Platform Engineer", "Build reliable systems", null, null, null,
                null, null, "en-US", CvPolicy.OPTIONAL, null, null, null, null);

        var response = service.createJob(tenantId, request);

        verify(jobs).saveAndFlush(any(RecruitmentJob.class));
        verify(projections).job(any(RecruitmentJob.class), org.mockito.ArgumentMatchers.isNull());
        assertThat(response.createdAt()).isEqualTo(persistedAt);
        assertThat(response.updatedAt()).isEqualTo(persistedAt);
    }

    private RecruitmentService service(
            RecruitmentJobRepository jobs,
            RecruitmentCapabilityService capabilities,
            ScreeningSupport screening) {
        return new RecruitmentService(
                mock(RecruitmentTenantSettingsRepository.class),
                jobs,
                mock(InterviewTemplateRepository.class),
                mock(InterviewTemplateRevisionRepository.class),
                mock(RecruitmentCandidateRepository.class),
                mock(RecruitmentApplicationRepository.class),
                mock(RecruitmentInterviewRepository.class),
                mock(HiringQuotaApi.class),
                mock(BillingModuleApi.class),
                mock(TenantPublicProfileApi.class),
                mock(RecruitmentQueryService.class),
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-07-30T09:00:00Z"), ZoneOffset.UTC),
                mock(ApplicationEventPublisher.class),
                screening,
                mock(JobDescriptionHtml.class),
                mock(RecruitmentPhoneNumbers.class),
                mock(ApplicationSubmissionTransitionService.class),
                mock(RecruitmentInterviewCancellationService.class),
                capabilities,
                callingProperties());
    }

    private RecruitmentCallingProperties callingProperties() {
        return new RecruitmentCallingProperties(
                false, false, 0, 0, 1, 1, 1, 1,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                false, false, false, "test", false);
    }
}
