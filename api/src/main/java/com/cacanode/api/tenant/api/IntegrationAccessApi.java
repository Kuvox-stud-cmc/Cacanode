package com.cacanode.api.tenant.api;

import java.util.List;
import java.util.UUID;

public interface IntegrationAccessApi {
    String WIDGET_SCOPE = "widget:chat";
    String API_SCOPE = "api:chat";

    IntegrationPrincipal authenticateChatAccess(
            String authorization, String requiredScope, String parentOrigin);

    IntegrationPrincipal authenticateForAnyChatScope(String authorization);

    void validateEvidenceAccess(UUID integrationTokenId, UUID tenantId, UUID knowledgeBaseId);

    record IntegrationPrincipal(
            UUID tokenId,
            UUID tenantId,
            UUID chatbotId,
            UUID knowledgeBaseId,
            List<String> scopes
    ) {
    }
}
