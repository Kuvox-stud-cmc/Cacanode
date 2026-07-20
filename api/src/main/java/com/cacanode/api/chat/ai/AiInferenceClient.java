package com.cacanode.api.chat.ai;

import com.cacanode.api.chat.dto.ChatDtos;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AiInferenceClient {
    GeneratedAnswer generate(GenerationRequest request);

    List<ChatDtos.DocumentUnitResponse> listDocumentUnits(
            UUID tenantId, UUID knowledgeBaseId, UUID documentId, String requestId);

    void deleteDocumentIndex(UUID tenantId, UUID knowledgeBaseId, UUID documentId, String requestId);

    record GenerationRequest(
            UUID generationId,
            UUID turnId,
            UUID tenantId,
            UUID chatbotId,
            UUID knowledgeBaseId,
            long authoritativeRevision,
            String channel,
            String locale,
            String question,
            List<PriorMessage> priorMessages,
            String tenantName,
            String customerAnswerPrompt,
            boolean customerVisibility,
            List<UUID> visibleDocumentIds,
            String promptSchemaVersion,
            String requestId,
            String traceId
    ) {
    }

    record PriorMessage(String role, String content) {
    }

    record GeneratedAnswer(
            UUID generationId,
            long authoritativeRevision,
            String answer,
            List<ChatDtos.CitationResponse> citations,
            Map<String, Object> action,
            Long inputTokens,
            Long outputTokens,
            String cacheTier,
            Long avoidedInputTokens,
            Long avoidedOutputTokens
    ) {
    }
}
