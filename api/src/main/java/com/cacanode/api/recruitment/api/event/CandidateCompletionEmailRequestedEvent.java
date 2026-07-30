package com.cacanode.api.recruitment.api.event;

import java.util.UUID;

public record CandidateCompletionEmailRequestedEvent(
        UUID tenantId, UUID applicationId, String email, String fullName,
        String companyName, String jobTitle, String locale, String completionUrl) {}
