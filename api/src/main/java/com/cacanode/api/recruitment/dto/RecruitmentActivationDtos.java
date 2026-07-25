package com.cacanode.api.recruitment.dto;

import com.cacanode.api.recruitment.model.RecruitmentEnums.RolloutStage;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class RecruitmentActivationDtos {
    private RecruitmentActivationDtos() {}

    public record Capabilities(UUID tenantId,RolloutStage rolloutStage,boolean masterEnabled,
            boolean publicJobsEnabled,boolean automationEnabled,boolean cvAiEnabled,
            boolean callingEnabled,boolean recordingEnabled,boolean publicDiscoveryEnabled,
            List<String> blockers) {}

    public record Activation(UUID tenantId,RolloutStage rolloutStage,boolean masterEnabled,
            boolean automationEnabled,boolean cvAiEnabled,boolean callingEnabled,
            boolean recordingEnabled,boolean publicDiscoveryEnabled,long version) {}

    public record ActivationUpdate(@NotNull RolloutStage rolloutStage,boolean masterEnabled,
            boolean automationEnabled,boolean cvAiEnabled,boolean callingEnabled,
            boolean recordingEnabled,boolean publicDiscoveryEnabled,@NotNull Long version) {}
}
