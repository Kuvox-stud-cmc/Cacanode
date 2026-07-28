package com.cacanode.api.recruitment.api;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public interface RecruitmentPlatformAdministrationApi {
    Activation activation(UUID tenantId);
    Activation update(UUID tenantId, UUID actorId, ActivationUpdate request, String ipAddress, String userAgent);

    enum RolloutStage { OFF, INTERNAL, PILOT, GA, AUTO }

    record Activation(UUID tenantId, RolloutStage rolloutStage, boolean masterEnabled,
                      boolean automationEnabled, boolean cvAiEnabled, boolean callingEnabled,
                      boolean recordingEnabled, boolean publicDiscoveryEnabled, long version) {}

    record ActivationUpdate(@NotNull RolloutStage rolloutStage, boolean masterEnabled,
                            boolean automationEnabled, boolean cvAiEnabled, boolean callingEnabled,
                            boolean recordingEnabled, boolean publicDiscoveryEnabled, @NotNull Long version) {}
}
