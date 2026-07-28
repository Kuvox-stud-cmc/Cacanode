package com.cacanode.api.recruitment.api.event;

import java.time.Instant;
import java.util.UUID;

public record RecruitmentCandidateEmailDispatchEvent(UUID deliveryId,String email,String candidateName,
        String companyName,String jobTitle,String locale,String managementUrl,String kind,
        Instant scheduledStartAt,String schedulingTimezone) {}
