package com.cacanode.api.recruitment.api;

import java.time.LocalDateTime;
import java.util.UUID;

public interface RecruitmentInterviewCommandApi {
    CreatedInterview create(CreateInterviewCommand command);

    record CreateInterviewCommand(UUID tenantId, UUID applicationId, LocalDateTime scheduledAt) {}
    record CreatedInterview(UUID interviewId, String status, String snapshotSha256) {}
}
