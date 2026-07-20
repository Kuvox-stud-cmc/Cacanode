package com.cacanode.api.tenant.service;

import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.cache.BusinessCache;
import com.cacanode.api.common.cache.BusinessCacheInvalidationPublisher;
import com.cacanode.api.common.cache.CacheKeyFactory;
import com.cacanode.api.common.cache.VersionedJsonCache;
import com.cacanode.api.common.event.AuditLogEvent;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.tenant.CustomerAnswerPromptDefaults;
import com.cacanode.api.tenant.dto.CustomerAnswerPromptDtos;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerAnswerPromptService {
    public static final int MAX_PROMPT_LENGTH = 4000;

    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;
    @Autowired(required = false)
    private VersionedJsonCache businessCache;
    @Autowired(required = false)
    private CacheKeyFactory cacheKeyFactory;
    @Autowired(required = false)
    private BusinessCacheInvalidationPublisher businessInvalidationPublisher;

    @Transactional(readOnly = true)
    public CustomerAnswerPromptDtos.Response get(UUID tenantId) {
        if (businessCache == null || cacheKeyFactory == null) {
            return loadAuthoritative(tenantId);
        }
        return businessCache.getOrLoad(
                BusinessCache.CUSTOMER_ANSWER_PROMPT,
                cacheKeyFactory.build("customer-answer-prompt", "tenant", tenantId.toString()),
                CustomerAnswerPromptDtos.Response.class,
                () -> loadAuthoritative(tenantId)
        );
    }

    @Transactional
    public CustomerAnswerPromptDtos.Response update(UUID tenantId, UUID actorId, String submittedPrompt) {
        if (submittedPrompt == null) {
            throw new BadRequestException("Prompt is required");
        }

        Tenant tenant = findTenant(tenantId);
        String trimmedPrompt = submittedPrompt.strip();
        boolean reset = trimmedPrompt.isEmpty();
        String prompt = reset
                ? CustomerAnswerPromptDefaults.forTenant(tenant.getName())
                : trimmedPrompt;
        int promptLength = prompt.codePointCount(0, prompt.length());
        if (promptLength > MAX_PROMPT_LENGTH) {
            throw new BadRequestException("Prompt must be 4,000 characters or fewer");
        }

        tenant.setCustomerAnswerPrompt(prompt);
        tenant = tenantRepository.saveAndFlush(tenant);

        eventPublisher.publishEvent(AuditLogEvent.builder(this)
                .tenantId(tenantId)
                .userId(actorId)
                .action(LogAction.CUSTOMER_ANSWER_PROMPT_UPDATED)
                .resourceType("tenant")
                .resourceId(tenantId)
                .metadata(Map.of(
                        "promptLength", promptLength,
                        "reset", reset
                ))
                .build());

        if (businessInvalidationPublisher != null) {
            businessInvalidationPublisher.prompt(tenantId);
        }

        return toResponse(tenant);
    }

    private CustomerAnswerPromptDtos.Response loadAuthoritative(UUID tenantId) {
        return toResponse(findTenant(tenantId));
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant was not found"));
    }

    private CustomerAnswerPromptDtos.Response toResponse(Tenant tenant) {
        String storedPrompt = tenant.getCustomerAnswerPrompt();
        String tenantDefault = CustomerAnswerPromptDefaults.forTenant(tenant.getName());
        String prompt = CustomerAnswerPromptDefaults.shouldUseTenantDefault(
                storedPrompt, tenant.getName()) ? tenantDefault : storedPrompt.strip();
        return new CustomerAnswerPromptDtos.Response(
                prompt,
                tenantDefault.equals(prompt),
                tenant.getUpdatedAt()
        );
    }
}
