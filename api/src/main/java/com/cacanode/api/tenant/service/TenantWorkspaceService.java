package com.cacanode.api.tenant.service;

import com.cacanode.api.ai.enums.ModelConfigStatus;
import com.cacanode.api.common.cache.BusinessCache;
import com.cacanode.api.common.cache.CacheKeyFactory;
import com.cacanode.api.common.cache.VersionedJsonCache;
import com.cacanode.api.ai.model.ModelConfigVersion;
import com.cacanode.api.ai.repository.ModelConfigVersionRepository;
import com.cacanode.api.common.exception.custom.InternalServerErrorException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.tenant.CustomerAnswerPromptDefaults;
import com.cacanode.api.tenant.dto.TenantWorkspaceResponse;
import com.cacanode.api.tenant.enums.ChatbotStatus;
import com.cacanode.api.tenant.enums.KnowledgeBaseStatus;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.KnowledgeBase;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.model.WidgetConfig;
import com.cacanode.api.tenant.repository.ChatbotRepository;
import com.cacanode.api.tenant.repository.KnowledgeBaseRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.WidgetConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantWorkspaceService {
    private static final String DEFAULT_KNOWLEDGE_BASE_SLUG = "default";
    private static final String DEFAULT_LOCALE = "vi-VN";
    private static final String DEFAULT_KNOWLEDGE_BASE_NAME = "Default Knowledge Base";
    private static final String DEFAULT_CHATBOT_NAME = "CacaNode Assistant";
    private static final String DEFAULT_WELCOME_MESSAGE = "Xin chao! Toi co the giup gi cho ban?";

    private final TenantRepository tenantRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ChatbotRepository chatbotRepository;
    private final WidgetConfigRepository widgetConfigRepository;
    private final ModelConfigVersionRepository modelConfigVersionRepository;
    @Autowired(required = false)
    private VersionedJsonCache businessCache;
    @Autowired(required = false)
    private CacheKeyFactory cacheKeyFactory;

    @Transactional
    public TenantWorkspaceResponse getOrProvisionWorkspace(UUID tenantId) {
        if (businessCache == null || cacheKeyFactory == null) {
            return loadOrProvisionAuthoritative(tenantId);
        }
        return businessCache.getOrLoad(
                BusinessCache.WORKSPACE,
                cacheKeyFactory.build("workspace", "tenant", tenantId.toString()),
                TenantWorkspaceResponse.class,
                () -> loadOrProvisionAuthoritative(tenantId)
        );
    }

    private TenantWorkspaceResponse loadOrProvisionAuthoritative(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant workspace was not found"));

        KnowledgeBase knowledgeBase = getOrCreateKnowledgeBase(tenant);
        Chatbot chatbot = getOrCreateChatbot(tenant, knowledgeBase);
        ensureWidgetConfig(tenant, chatbot);

        return toResponse(tenant.getId(), knowledgeBase, chatbot);
    }

    @Transactional
    public void provisionDefaultWorkspace(Tenant tenant) {
        KnowledgeBase knowledgeBase = getOrCreateKnowledgeBase(tenant);
        Chatbot chatbot = getOrCreateChatbot(tenant, knowledgeBase);
        ensureWidgetConfig(tenant, chatbot);
    }

    private KnowledgeBase getOrCreateKnowledgeBase(Tenant tenant) {
        return knowledgeBaseRepository.findByTenantIdAndSlug(tenant.getId(), DEFAULT_KNOWLEDGE_BASE_SLUG)
                .map(knowledgeBase -> {
                    if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
                        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
                    }
                    return knowledgeBase;
                })
                .orElseGet(() -> {
                    KnowledgeBase knowledgeBase = new KnowledgeBase();
                    knowledgeBase.setTenant(tenant);
                    knowledgeBase.setName(DEFAULT_KNOWLEDGE_BASE_NAME);
                    knowledgeBase.setSlug(DEFAULT_KNOWLEDGE_BASE_SLUG);
                    knowledgeBase.setDescription("Default tenant-scoped knowledge base.");
                    knowledgeBase.setDefaultLocale(DEFAULT_LOCALE);
                    knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
                    return knowledgeBaseRepository.save(knowledgeBase);
                });
    }

    private Chatbot getOrCreateChatbot(Tenant tenant, KnowledgeBase knowledgeBase) {
        return chatbotRepository
                .findFirstByTenant_IdAndKnowledgeBase_IdAndStatusOrderByCreatedAtAsc(
                        tenant.getId(),
                        knowledgeBase.getId(),
                        ChatbotStatus.ACTIVE
                )
                .orElseGet(() -> {
                    ModelConfigVersion modelConfigVersion = modelConfigVersionRepository
                            .findFirstByStatusOrderByCreatedAtDesc(ModelConfigStatus.ACTIVE)
                            .orElseThrow(() -> new InternalServerErrorException(
                                    "Cannot provision tenant workspace: no active model configuration exists"
                            ));

                    Chatbot chatbot = new Chatbot();
                    chatbot.setTenant(tenant);
                    chatbot.setKnowledgeBase(knowledgeBase);
                    chatbot.setModelConfigVersion(modelConfigVersion);
                    chatbot.setDisplayName(DEFAULT_CHATBOT_NAME);
                    chatbot.setDefaultLocale(knowledgeBase.getDefaultLocale());
                    chatbot.setWelcomeMessage(DEFAULT_WELCOME_MESSAGE);
                    chatbot.setSafeInstructions(CustomerAnswerPromptDefaults.forTenant(tenant.getName()));
                    chatbot.setRetrievalSettings(Map.of(
                            "topK", 8,
                            "graphDepth", 2,
                            "rerank", true,
                            "minScore", 0.35
                    ));
                    chatbot.setStatus(ChatbotStatus.ACTIVE);
                    return chatbotRepository.save(chatbot);
                });
    }

    private void ensureWidgetConfig(Tenant tenant, Chatbot chatbot) {
        if (widgetConfigRepository.existsByChatbot_Id(chatbot.getId())) {
            return;
        }

        WidgetConfig widgetConfig = new WidgetConfig();
        widgetConfig.setTenant(tenant);
        widgetConfig.setChatbot(chatbot);
        widgetConfig.setDisplayName(chatbot.getDisplayName());
        widgetConfig.setWelcomeMessage(chatbot.getWelcomeMessage());
        widgetConfigRepository.save(widgetConfig);
    }

    private TenantWorkspaceResponse toResponse(UUID tenantId, KnowledgeBase knowledgeBase, Chatbot chatbot) {
        return new TenantWorkspaceResponse(
                tenantId,
                new TenantWorkspaceResponse.KnowledgeBaseWorkspace(
                        knowledgeBase.getId(),
                        knowledgeBase.getName(),
                        knowledgeBase.getSlug(),
                        knowledgeBase.getDefaultLocale()
                ),
                new TenantWorkspaceResponse.ChatbotWorkspace(
                        chatbot.getId(),
                        chatbot.getDisplayName(),
                        chatbot.getDefaultLocale(),
                        chatbot.getWelcomeMessage()
                )
        );
    }
}
