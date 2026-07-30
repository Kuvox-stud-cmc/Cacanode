package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.event.durable.DurableEventPublisher;
import com.cacanode.api.recruitment.api.event.RecruitmentApplicationProjectionChangedEvent;
import com.cacanode.api.recruitment.api.event.RecruitmentInterviewProjectionChangedEvent;
import com.cacanode.api.recruitment.api.event.RecruitmentJobProjectionChangedEvent;
import com.cacanode.api.recruitment.model.RecruitmentApplication;
import com.cacanode.api.recruitment.model.RecruitmentInterview;
import com.cacanode.api.recruitment.model.RecruitmentJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.recruitment", name = "enabled", havingValue = "true")
public class RecruitmentProjectionEventPublisher {
    private final DurableEventPublisher events;

    public void job(RecruitmentJob job, String businessEvent) {
        requireAuditTimestamps("Recruitment job", job.getCreatedAt(), job.getUpdatedAt());
        events.publish("recruitment.job.projection.v1", 1, new RecruitmentJobProjectionChangedEvent(
                job.getTenantId(), job.getId(), job.getStatus().name(), businessEvent,
                job.getCreatedAt(), job.getUpdatedAt(), job.getPublishedAt(), job.getPausedAt(),
                job.getClosedAt(), job.getArchivedAt()));
    }

    public void application(RecruitmentApplication application, String businessEvent) {
        events.publish("recruitment.application.projection.v1", 1,
                new RecruitmentApplicationProjectionChangedEvent(
                        application.getTenantId(), application.getId(), application.getJobId(),
                        application.getStatus().name(), businessEvent, application.getCreatedAt(),
                        application.getUpdatedAt(), application.getSubmittedAt(), application.getVerifiedAt(),
                        application.getWithdrawnAt()));
    }

    public void interview(RecruitmentInterview interview, String businessEvent) {
        events.publish("recruitment.interview.projection.v1", 1,
                new RecruitmentInterviewProjectionChangedEvent(
                        interview.getTenantId(), interview.getId(), interview.getApplicationId(),
                        interview.getJobId(), interview.getStatus().name(), businessEvent,
                        interview.getCreatedAt(), interview.getUpdatedAt(), interview.getInvitedAt(),
                        interview.getScheduledStartAt(), interview.getScheduledEndAt(),
                        interview.getSchedulingTimezone(), interview.getRescheduleCount(),
                        interview.getStartedAt(), interview.getCompletedAt(), interview.getCancelledAt(),
                        interview.getExpiredAt()));
    }

    private static void requireAuditTimestamps(
            String aggregateName, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (createdAt == null || updatedAt == null) {
            throw new IllegalStateException(aggregateName + " audit timestamps are not initialized");
        }
    }
}
