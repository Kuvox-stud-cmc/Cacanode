package com.cacanode.api.recruitment.listener;

import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import com.cacanode.api.recruitment.model.RecruitmentEnums.RolloutStage;
import com.cacanode.api.recruitment.model.RecruitmentTenantActivation;
import com.cacanode.api.recruitment.repository.RecruitmentTenantActivationRepository;
import com.cacanode.api.tenant.api.event.TenantCreatedEvent;
import com.cacanode.api.tenant.api.TenantKind;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecruitmentTenantActivationListener {
    private static final String CONSUMER_NAME = "recruitment.default-tenant-activation";

    private final RecruitmentProperties properties;
    private final RecruitmentTenantActivationRepository activations;
    private final ModuleEventInboxService inboxService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTenantCreated(TenantCreatedEvent event) {
        if (event.kind() != TenantKind.CUSTOMER) return;
        if (!properties.autoActivateNewTenants() || !inboxService.claim(CONSUMER_NAME)) return;

        RecruitmentTenantActivation activation = activations.findById(event.tenantId()).orElseGet(() -> {
            RecruitmentTenantActivation value = new RecruitmentTenantActivation();
            value.setTenantId(event.tenantId());
            return value;
        });
        if (activation.isMasterEnabled() || activation.getRolloutStage() != RolloutStage.OFF) return;

        activation.setRolloutStage(RolloutStage.AUTO);
        activation.setMasterEnabled(true);
        activation.setAutomationEnabled(properties.automationEnabled());
        activation.setCvAiEnabled(properties.cvAiEnabled());
        activation.setCallingEnabled(properties.callingEnabled());
        activation.setRecordingEnabled(properties.recordingEnabled());
        activation.setPublicDiscoveryEnabled(false);
        activations.saveAndFlush(activation);
    }
}
