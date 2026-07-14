package com.cacanode.api.tenant.service;

import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.event.AuditLogEvent;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.tenant.CustomerAnswerPromptDefaults;
import com.cacanode.api.tenant.dto.CustomerAnswerPromptDtos;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
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

    @Transactional(readOnly = true)
    public CustomerAnswerPromptDtos.Response get(UUID tenantId) {
        return toResponse(findTenant(tenantId));
    }

    @Transactional
    public CustomerAnswerPromptDtos.Response update(UUID tenantId, UUID actorId, String submittedPrompt) {
        if (submittedPrompt == null) {
            throw new BadRequestException("Prompt is required");
        }

        String trimmedPrompt = submittedPrompt.strip();
        boolean reset = trimmedPrompt.isEmpty();
        String prompt = reset ? CustomerAnswerPromptDefaults.PLATFORM_DEFAULT : trimmedPrompt;
        int promptLength = prompt.codePointCount(0, prompt.length());
        if (promptLength > MAX_PROMPT_LENGTH) {
            throw new BadRequestException("Prompt must be 4,000 characters or fewer");
        }

        Tenant tenant = findTenant(tenantId);
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

        return toResponse(tenant);
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant was not found"));
    }

    private CustomerAnswerPromptDtos.Response toResponse(Tenant tenant) {
        String storedPrompt = tenant.getCustomerAnswerPrompt();
        String prompt = storedPrompt == null || storedPrompt.strip().isEmpty()
                ? CustomerAnswerPromptDefaults.PLATFORM_DEFAULT
                : storedPrompt.strip();
        return new CustomerAnswerPromptDtos.Response(
                prompt,
                CustomerAnswerPromptDefaults.PLATFORM_DEFAULT.equals(prompt),
                tenant.getUpdatedAt()
        );
    }
}
