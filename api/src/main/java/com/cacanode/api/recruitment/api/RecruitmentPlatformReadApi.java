package com.cacanode.api.recruitment.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Safe, read-only recruitment data published for platform administration. */
public interface RecruitmentPlatformReadApi {
    JobPage jobs(JobQuery query);

    JobDetail job(UUID jobId);

    enum JobStatus { DRAFT, PUBLISHED, PAUSED, CLOSED, ARCHIVED }
    enum EmploymentType { FULL_TIME, PART_TIME, CONTRACT, TEMPORARY, INTERNSHIP }
    enum WorkMode { ONSITE, REMOTE, HYBRID }
    enum ExperienceLevel { ENTRY, JUNIOR, MID, SENIOR, LEAD, EXECUTIVE }
    enum Visibility { VISIBLE, HIDDEN }
    enum Sort {
        TITLE, COMPANY_NAME, STATUS, PUBLISHED_AT, CLOSING_AT, UPDATED_AT,
        APPLICATIONS, INTERVIEWS, VISIBILITY
    }
    enum Direction { ASC, DESC }

    record JobQuery(
            int page,
            int size,
            UUID tenantId,
            JobStatus status,
            String search,
            String language,
            String department,
            String location,
            EmploymentType employmentType,
            WorkMode workMode,
            Visibility visibility,
            Instant closingFrom,
            Instant closingTo,
            Instant updatedFrom,
            Instant updatedTo,
            Sort sort,
            Direction direction) {
    }

    record JobPage(List<JobItem> items, int page, int size, long total) {
    }

    record JobItem(
            UUID jobId,
            UUID publicId,
            UUID tenantId,
            String frozenCompanyName,
            String title,
            JobStatus status,
            String department,
            String location,
            String language,
            EmploymentType employmentType,
            WorkMode workMode,
            ExperienceLevel experienceLevel,
            Instant publishedAt,
            Instant closingAt,
            Instant updatedAt,
            boolean discoverable,
            boolean visibleOnPublicBoard,
            long totalApplications,
            long totalInterviews) {
    }

    record JobDetail(
            UUID jobId,
            UUID publicId,
            UUID tenantId,
            String frozenCompanyName,
            String title,
            JobStatus status,
            String department,
            String location,
            String language,
            EmploymentType employmentType,
            WorkMode workMode,
            ExperienceLevel experienceLevel,
            Instant publishedAt,
            Instant closingAt,
            Instant updatedAt,
            boolean discoverable,
            boolean visibleOnPublicBoard,
            long totalApplications,
            long verifiedApplications,
            long totalInterviews,
            long completedInterviews,
            long unsuccessfulInterviews) {
    }
}
