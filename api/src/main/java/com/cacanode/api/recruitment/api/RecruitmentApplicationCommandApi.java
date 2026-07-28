package com.cacanode.api.recruitment.api;

import java.util.UUID;

public interface RecruitmentApplicationCommandApi {
    CreatedApplication create(CreateApplicationCommand command);

    record CreateApplicationCommand(UUID tenantId, UUID jobId, UUID candidateId, boolean cvPresent) {}
    record CreatedApplication(UUID applicationId, String status, String snapshotSha256) {}
}
