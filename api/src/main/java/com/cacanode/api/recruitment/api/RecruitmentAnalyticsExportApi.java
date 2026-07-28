package com.cacanode.api.recruitment.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RecruitmentAnalyticsExportApi {
    SnapshotPage<JobStatusSnapshot> exportJobs(UUID tenantId, String cursor, int limit);

    SnapshotPage<ApplicationStatusSnapshot> exportApplications(UUID tenantId, String cursor, int limit);

    SnapshotPage<InterviewStatusSnapshot> exportInterviews(UUID tenantId, String cursor, int limit);

    record SnapshotPage<T>(List<T> items, String nextCursor) {
        public SnapshotPage {
            items = List.copyOf(items);
        }
    }

    record JobStatusSnapshot(UUID jobId, String status, Instant createdAt, Instant updatedAt,
                             Instant publishedAt, Instant pausedAt, Instant closedAt, Instant archivedAt) {
    }

    record ApplicationStatusSnapshot(
            UUID applicationId, UUID jobId, String status, Instant createdAt, Instant updatedAt,
            Instant submittedAt, Instant verifiedAt, Instant withdrawnAt) {
    }

    record InterviewStatusSnapshot(
            UUID interviewId, UUID applicationId, UUID jobId, String status,
            Instant createdAt, Instant updatedAt, Instant invitedAt, Instant scheduledStartAt,
            Instant scheduledEndAt, Instant startedAt, Instant completedAt,
            Instant cancelledAt, Instant expiredAt) {
    }
}
