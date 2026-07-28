package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.BillingModuleApi;
import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.event.AuditLogEvent;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.config.RecruitmentActivationProperties;
import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import com.cacanode.api.recruitment.dto.RecruitmentActivationDtos;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.model.RecruitmentTenantActivation;
import com.cacanode.api.recruitment.model.RecruitmentTenantSettings;
import com.cacanode.api.recruitment.query.PublicJobProjectionService;
import com.cacanode.api.recruitment.repository.RecruitmentActivationOperationsRepository;
import com.cacanode.api.recruitment.repository.RecruitmentJobRepository;
import com.cacanode.api.recruitment.repository.RecruitmentTenantActivationRepository;
import com.cacanode.api.recruitment.repository.RecruitmentTenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class RecruitmentCapabilityService {
    private final RecruitmentTenantActivationRepository activations;
    private final RecruitmentTenantSettingsRepository settings;
    private final RecruitmentJobRepository jobs;
    private final BillingModuleApi billing;
    private final RecruitmentProperties global;
    private final RecruitmentCallingProperties calling;
    private final RecruitmentActivationProperties rollout;
    private final ApplicationEventPublisher events;
    private final RecruitmentActivationOperationsRepository activationOperations;
    @Autowired(required=false) private PublicJobProjectionService publicJobs;

    @Transactional(readOnly=true)
    public RecruitmentActivationDtos.Capabilities capabilities(UUID tenantId) {
        RecruitmentTenantActivation activation=activations.findById(tenantId).orElseGet(()->off(tenantId));
        RecruitmentTenantSettings preference=settings.findById(tenantId).orElse(null);
        List<String> blockers=new ArrayList<>();
        boolean master=global.enabled()&&activation.isMasterEnabled()&&activation.getRolloutStage()!=RolloutStage.OFF;
        if(!global.enabled())blockers.add("DEPLOYMENT_DISABLED");
        else if(!activation.isMasterEnabled()||activation.getRolloutStage()==RolloutStage.OFF)blockers.add("PLATFORM_NOT_ACTIVATED");
        boolean publicEnabled=master&&global.publicJobsEnabled();
        boolean automation=master&&global.automationEnabled()&&activation.isAutomationEnabled()
                &&preference!=null&&preference.getDefaultAutomationMode()!=AutomationMode.MANUAL;
        boolean cvAi=master&&global.messagingEnabled()&&global.cvAiEnabled()&&activation.isCvAiEnabled()
                &&preference!=null&&preference.getCvAiMode()!=CvAiMode.OFF;
        boolean calling=master&&global.messagingEnabled()&&global.callingEnabled()&&activation.isCallingEnabled();
        boolean recording=calling&&global.recordingEnabled()&&activation.isRecordingEnabled()
                &&preference!=null&&preference.isRecordingEnabled();
        boolean discovery=publicEnabled&&activation.getRolloutStage()==RolloutStage.GA
                &&rollout.gaUnlocked()&&activation.isPublicDiscoveryEnabled();
        if(master&&!global.publicJobsEnabled())blockers.add("PUBLIC_JOBS_DEPLOYMENT_DISABLED");
        if(activation.getRolloutStage()==RolloutStage.GA&&!rollout.gaUnlocked())blockers.add("GA_LOCKED");
        return new RecruitmentActivationDtos.Capabilities(tenantId,activation.getRolloutStage(),master,
                publicEnabled,automation,cvAi,calling,recording,discovery,
                this.calling.callForMoreThan600()?14400:600,List.copyOf(blockers));
    }

    @Transactional(readOnly=true)
    public RecruitmentActivationDtos.Activation activation(UUID tenantId) {
        return response(activations.findById(tenantId).orElseGet(()->off(tenantId)));
    }

    @Transactional
    public RecruitmentActivationDtos.Activation update(UUID tenantId,UUID actorId,
            RecruitmentActivationDtos.ActivationUpdate request,String ipAddress,String userAgent) {
        validate(request,tenantId);
        RecruitmentTenantActivation value=activations.findForUpdate(tenantId).orElseGet(()->off(tenantId));
        if(value.getVersion()!=request.version())throw new OptimisticLockingFailureException("Recruitment activation was updated concurrently");
        RolloutStage previous=value.getRolloutStage();boolean previousMaster=value.isMasterEnabled();
        value.setRolloutStage(request.rolloutStage());value.setMasterEnabled(request.masterEnabled());
        value.setAutomationEnabled(request.automationEnabled());value.setCvAiEnabled(request.cvAiEnabled());
        value.setCallingEnabled(request.callingEnabled());value.setRecordingEnabled(request.recordingEnabled());
        value.setPublicDiscoveryEnabled(request.publicDiscoveryEnabled());value=activations.saveAndFlush(value);
        reconcile(value);
        events.publishEvent(AuditLogEvent.builder(this).tenantId(tenantId).userId(actorId)
                .action(LogAction.RECRUITMENT_ACTIVATION_UPDATED).resourceType("recruitment_tenant_activation")
                .resourceId(tenantId).ipAddress(ipAddress).userAgent(userAgent)
                .metadata(Map.of("previousStage",previous.name(),"stage",value.getRolloutStage().name(),
                        "previousMasterEnabled",previousMaster,"masterEnabled",value.isMasterEnabled())).build());
        return response(value);
    }

    public void requireMasterEnabled(UUID tenantId) {
        if(!capabilities(tenantId).masterEnabled())throw new ConflictException("Recruitment is not activated for this tenant");
    }

    public void requireAutomation(UUID tenantId) {
        if(!capabilities(tenantId).automationEnabled())throw new ConflictException("Recruitment automation is disabled");
    }

    public void requireCvAi(UUID tenantId) {
        if(!capabilities(tenantId).cvAiEnabled())throw new ConflictException("Recruitment CV AI is disabled");
    }

    public void requireCalling(UUID tenantId) {
        if(!capabilities(tenantId).callingEnabled())throw new ConflictException("Recruitment calling is disabled");
    }

    private void validate(RecruitmentActivationDtos.ActivationUpdate request,UUID tenantId) {
        if(!request.masterEnabled()&&(request.automationEnabled()||request.cvAiEnabled()||request.callingEnabled()
                ||request.recordingEnabled()||request.publicDiscoveryEnabled()))
            throw new ConflictException("Tenant capability gates require master enablement");
        if(request.rolloutStage()==RolloutStage.OFF&&request.masterEnabled())
            throw new ConflictException("OFF stage cannot be enabled");
        if(request.recordingEnabled()&&!request.callingEnabled())
            throw new ConflictException("Recording requires calling");
        if(request.publicDiscoveryEnabled()&&request.rolloutStage()!=RolloutStage.GA)
            throw new ConflictException("Public discovery requires GA stage");
        if(request.rolloutStage()==RolloutStage.GA&&!rollout.gaUnlocked())
            throw new ConflictException("GA activation is locked by deployment configuration");
        if(request.rolloutStage()==RolloutStage.PILOT) {
            BillingPlanCode plan=billing.account(tenantId).planCode();
            if(plan!=BillingPlanCode.PRO&&plan!=BillingPlanCode.BUSINESS)
                throw new ConflictException("PILOT activation requires a Pro or Business plan");
        }
    }

    private void reconcile(RecruitmentTenantActivation activation) {
        if(!activation.isMasterEnabled()||!activation.isRecordingEnabled())
            activationOperations.requestRecordingStops(activation.getTenantId());
        if(publicJobs==null)return;
        for(var job:jobs.findByTenantId(activation.getTenantId()))publicJobs.synchronize(activation.getTenantId(),job.getId());
    }

    private static RecruitmentTenantActivation off(UUID tenantId) {
        RecruitmentTenantActivation value=new RecruitmentTenantActivation();value.setTenantId(tenantId);return value;
    }
    private static RecruitmentActivationDtos.Activation response(RecruitmentTenantActivation value) {
        return new RecruitmentActivationDtos.Activation(value.getTenantId(),value.getRolloutStage(),value.isMasterEnabled(),
                value.isAutomationEnabled(),value.isCvAiEnabled(),value.isCallingEnabled(),value.isRecordingEnabled(),
                value.isPublicDiscoveryEnabled(),value.getVersion());
    }
}
