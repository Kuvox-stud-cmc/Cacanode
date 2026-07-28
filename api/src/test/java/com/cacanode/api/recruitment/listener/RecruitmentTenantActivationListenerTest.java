package com.cacanode.api.recruitment.listener;

import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import com.cacanode.api.recruitment.model.RecruitmentEnums.RolloutStage;
import com.cacanode.api.recruitment.model.RecruitmentTenantActivation;
import com.cacanode.api.recruitment.repository.RecruitmentTenantActivationRepository;
import com.cacanode.api.tenant.api.event.TenantCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecruitmentTenantActivationListenerTest {
    @Mock RecruitmentTenantActivationRepository activations;
    @Mock ModuleEventInboxService inboxService;

    @Test
    void automaticallyActivatesANewTenantUsingEnabledDeploymentCapabilities() {
        RecruitmentProperties properties = new RecruitmentProperties(
                true, true, true, true, true, true, true, true);
        RecruitmentTenantActivationListener listener =
                new RecruitmentTenantActivationListener(properties, activations, inboxService);
        TenantCreatedEvent event = event();
        when(inboxService.claim(anyString())).thenReturn(true);
        when(activations.findById(event.tenantId())).thenReturn(Optional.empty());

        listener.onTenantCreated(event);

        ArgumentCaptor<RecruitmentTenantActivation> captor =
                ArgumentCaptor.forClass(RecruitmentTenantActivation.class);
        verify(activations).saveAndFlush(captor.capture());
        RecruitmentTenantActivation value = captor.getValue();
        assertEquals(event.tenantId(), value.getTenantId());
        assertEquals(RolloutStage.AUTO, value.getRolloutStage());
        assertTrue(value.isMasterEnabled());
        assertTrue(value.isAutomationEnabled());
        assertTrue(value.isCvAiEnabled());
        assertTrue(value.isCallingEnabled());
        assertTrue(value.isRecordingEnabled());
        assertFalse(value.isPublicDiscoveryEnabled());
    }

    @Test
    void leavesNewTenantsOffWhenAutomaticActivationIsDisabled() {
        RecruitmentProperties properties = new RecruitmentProperties(
                true, false, true, true, true, true, true, true);
        RecruitmentTenantActivationListener listener =
                new RecruitmentTenantActivationListener(properties, activations, inboxService);

        listener.onTenantCreated(event());

        verifyNoInteractions(activations, inboxService);
    }

    @Test
    void doesNotOverwriteAnActivationAlreadyManagedByAnAdministrator() {
        RecruitmentProperties properties = new RecruitmentProperties(
                true, true, true, true, true, true, true, true);
        RecruitmentTenantActivationListener listener =
                new RecruitmentTenantActivationListener(properties, activations, inboxService);
        TenantCreatedEvent event = event();
        RecruitmentTenantActivation existing = new RecruitmentTenantActivation();
        existing.setTenantId(event.tenantId());
        existing.setRolloutStage(RolloutStage.PILOT);
        existing.setMasterEnabled(true);
        when(inboxService.claim(anyString())).thenReturn(true);
        when(activations.findById(event.tenantId())).thenReturn(Optional.of(existing));

        listener.onTenantCreated(event);

        verify(activations, never()).saveAndFlush(any());
    }

    private static TenantCreatedEvent event() {
        LocalDateTime now = LocalDateTime.now();
        return new TenantCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), now, now.plusDays(14),
                "Automatic tenant", "TRIAL", "TRIAL", 10240, now);
    }
}
