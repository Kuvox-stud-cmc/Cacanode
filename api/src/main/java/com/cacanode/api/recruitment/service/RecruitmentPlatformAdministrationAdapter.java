package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.api.RecruitmentPlatformAdministrationApi;
import com.cacanode.api.recruitment.dto.RecruitmentActivationDtos;
import com.cacanode.api.recruitment.model.RecruitmentEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.recruitment", name = "enabled", havingValue = "true")
public class RecruitmentPlatformAdministrationAdapter implements RecruitmentPlatformAdministrationApi {
    private final RecruitmentCapabilityService service;

    @Override
    public Activation activation(UUID tenantId) {
        return toApi(service.activation(tenantId));
    }

    @Override
    public Activation update(UUID tenantId, UUID actorId, ActivationUpdate request,
                             String ipAddress, String userAgent) {
        if (request.rolloutStage() == null || request.version() == null) {
            throw new IllegalArgumentException("rolloutStage and version are required");
        }
        var body = new RecruitmentActivationDtos.ActivationUpdate(
                RecruitmentEnums.RolloutStage.valueOf(request.rolloutStage().name()), request.masterEnabled(),
                request.automationEnabled(), request.cvAiEnabled(), request.callingEnabled(),
                request.recordingEnabled(), request.publicDiscoveryEnabled(), request.version());
        return toApi(service.update(tenantId, actorId, body, ipAddress, userAgent));
    }

    private static Activation toApi(RecruitmentActivationDtos.Activation value) {
        return new Activation(value.tenantId(), RolloutStage.valueOf(value.rolloutStage().name()),
                value.masterEnabled(), value.automationEnabled(), value.cvAiEnabled(), value.callingEnabled(),
                value.recordingEnabled(), value.publicDiscoveryEnabled(), value.version());
    }
}
