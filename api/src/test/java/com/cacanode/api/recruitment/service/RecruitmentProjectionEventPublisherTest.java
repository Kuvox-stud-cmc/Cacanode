package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.event.durable.DurableEventPublisher;
import com.cacanode.api.recruitment.api.event.RecruitmentJobProjectionChangedEvent;
import com.cacanode.api.recruitment.model.RecruitmentEnums.JobStatus;
import com.cacanode.api.recruitment.model.RecruitmentJob;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RecruitmentProjectionEventPublisherTest {
    @Test
    void publishesInitializedJobAuditTimestamps() {
        DurableEventPublisher events = mock(DurableEventPublisher.class);
        RecruitmentProjectionEventPublisher publisher = new RecruitmentProjectionEventPublisher(events);
        RecruitmentJob job = job();
        LocalDateTime createdAt = LocalDateTime.parse("2026-07-30T09:00:00");
        LocalDateTime updatedAt = createdAt.plusMinutes(1);
        job.setCreatedAt(createdAt);
        job.setUpdatedAt(updatedAt);

        publisher.job(job, null);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(org.mockito.ArgumentMatchers.eq("recruitment.job.projection.v1"),
                org.mockito.ArgumentMatchers.eq(1), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(RecruitmentJobProjectionChangedEvent.class);
        RecruitmentJobProjectionChangedEvent event = (RecruitmentJobProjectionChangedEvent) payload.getValue();
        assertThat(event.createdAt()).isEqualTo(createdAt);
        assertThat(event.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void rejectsUninitializedTimestampsBeforePoisoningTheOutbox() {
        DurableEventPublisher events = mock(DurableEventPublisher.class);
        RecruitmentProjectionEventPublisher publisher = new RecruitmentProjectionEventPublisher(events);

        assertThatThrownBy(() -> publisher.job(job(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Recruitment job audit timestamps are not initialized");
        verifyNoInteractions(events);
    }

    private RecruitmentJob job() {
        RecruitmentJob job = new RecruitmentJob();
        job.setId(UUID.randomUUID());
        job.setTenantId(UUID.randomUUID());
        job.setStatus(JobStatus.DRAFT);
        return job;
    }
}
