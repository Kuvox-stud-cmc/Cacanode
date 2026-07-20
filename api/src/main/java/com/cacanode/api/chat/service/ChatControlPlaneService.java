package com.cacanode.api.chat.service;

import com.cacanode.api.billing.model.UsageMetrics;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.repository.UsageMetricsRepository;
import com.cacanode.api.billing.service.BillingPeriods;
import com.cacanode.api.chat.ai.AiInferenceClient;
import com.cacanode.api.chat.dto.ChatDtos;
import com.cacanode.api.chat.enums.ChatChannel;
import com.cacanode.api.chat.enums.ChatSessionStatus;
import com.cacanode.api.chat.enums.ChatTurnStatus;
import com.cacanode.api.chat.exception.ChatApiException;
import com.cacanode.api.chat.model.ChatMessage;
import com.cacanode.api.chat.model.ChatSession;
import com.cacanode.api.chat.model.ChatTurn;
import com.cacanode.api.chat.repository.ChatMessageRepository;
import com.cacanode.api.chat.repository.ChatSessionRepository;
import com.cacanode.api.chat.repository.ChatTurnRepository;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.document.enums.DocumentVisibility;
import com.cacanode.api.document.model.Document;
import com.cacanode.api.document.repository.DocumentRepository;
import com.cacanode.api.integration.service.WebhookService;
import com.cacanode.api.notification.enums.NotificationStatus;
import com.cacanode.api.notification.enums.NotificationType;
import com.cacanode.api.notification.model.Notification;
import com.cacanode.api.notification.repository.NotificationRepository;
import com.cacanode.api.tenant.enums.ChatbotStatus;
import com.cacanode.api.tenant.enums.KnowledgeBaseStatus;
import com.cacanode.api.tenant.model.Chatbot;
import com.cacanode.api.tenant.model.KnowledgeBase;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.enums.TenantStatus;
import com.cacanode.api.tenant.repository.ChatbotRepository;
import com.cacanode.api.tenant.repository.KnowledgeBaseRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatControlPlaneService {
    private static final String PROMPT_SCHEMA_VERSION = "chat-prompts-v2";
    private static final List<ChatChannel> EXTERNAL_CHANNELS =
            List.of(ChatChannel.WIDGET, ChatChannel.CUSTOM_API);

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatTurnRepository turnRepository;
    private final TenantRepository tenantRepository;
    private final ChatbotRepository chatbotRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final UsageMetricsRepository usageRepository;
    private final BillingPeriods billingPeriods;
    private final NotificationRepository notificationRepository;
    private final WebhookService webhookService;
    private final AiInferenceClient inferenceClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public ChatDtos.SessionResponse createEmployeeSession(
            UUID tenantId, UUID userId, ChatDtos.CreateSessionRequest request) {
        return transactions.execute(status -> createSession(
                tenantId, userId, request.chatbotId(), request.knowledgeBaseId(),
                request.locale(), ChatChannel.EMPLOYEE_PLAYGROUND, request.externalUserId(),
                null, null, request.metadata(), null));
    }

    public ChatDtos.SessionResponse createExternalSession(
            UUID tenantId, UUID chatbotId, UUID knowledgeBaseId, UUID tokenId,
            ChatChannel channel, ChatDtos.ExternalCreateSessionRequest request) {
        return transactions.execute(status -> createSession(
                tenantId, null, chatbotId, knowledgeBaseId, request.locale(), channel,
                request.externalUserId() == null || request.externalUserId().isBlank()
                        ? "visitor_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                        : request.externalUserId(),
                request.customerName(), request.customerEmail(), request.metadata(), tokenId));
    }

    public ChatDtos.AssistantMessageResponse submitEmployeeMessage(
            UUID tenantId, UUID userId, UUID sessionId, String content,
            Map<String, Object> metadata, String idempotencyKey, String requestId) {
        return submit(tenantId, userId, null, sessionId, content, metadata,
                idempotencyKey, requestId);
    }

    public ChatDtos.AssistantMessageResponse submitExternalMessage(
            UUID tenantId, UUID tokenId, UUID sessionId, String content,
            Map<String, Object> metadata, String idempotencyKey, String requestId) {
        return submit(tenantId, null, tokenId, sessionId, content, metadata,
                idempotencyKey, requestId);
    }

    private ChatDtos.AssistantMessageResponse submit(
            UUID tenantId, UUID userId, UUID tokenId, UUID sessionId, String content,
            Map<String, Object> metadata, String idempotencyKey, String requestId) {
        PreparedTurn prepared = transactions.execute(status -> prepareTurn(
                tenantId, userId, tokenId, sessionId, content, metadata,
                idempotencyKey, requestId));
        Objects.requireNonNull(prepared);
        if (prepared.replayed() != null) {
            return prepared.replayed();
        }

        GenerationContext context = prepared.context();
        for (int revisionAttempt = 0; revisionAttempt < 2; revisionAttempt++) {
            try {
                AiInferenceClient.GeneratedAnswer answer = inferenceClient.generate(context.toRequest());
                GenerationContext completedContext = context;
                FinalizeResult finalized = transactions.execute(status -> finalizeTurn(
                        prepared.turnId(), answer, completedContext));
                Objects.requireNonNull(finalized);
                if (!finalized.revisionChanged()) {
                    return finalized.response();
                }
                if (revisionAttempt == 1) {
                    markFailed(prepared.turnId(), "REVISION_CHANGED");
                    throw new ChatApiException(HttpStatus.CONFLICT, "KNOWLEDGE_BASE_CHANGED",
                            "The knowledge base changed while the answer was generated.");
                }
                context = transactions.execute(status -> rebuildContext(prepared.turnId(), requestId));
                Objects.requireNonNull(context);
            } catch (ChatApiException exception) {
                if (!"KNOWLEDGE_BASE_CHANGED".equals(exception.getCode())) {
                    markFailed(prepared.turnId(), exception.getCode());
                }
                throw exception;
            } catch (RuntimeException exception) {
                markFailed(prepared.turnId(), "AI_FAILURE");
                throw new ChatApiException(HttpStatus.BAD_GATEWAY, "MODEL_PROVIDER_ERROR",
                        "The model provider could not complete the request.");
            }
        }
        throw new IllegalStateException("Unreachable generation state");
    }

    private PreparedTurn prepareTurn(
            UUID tenantId, UUID userId, UUID tokenId, UUID sessionId, String content,
            Map<String, Object> metadata, String idempotencyKey, String requestId) {
        ChatSession session = lockedAuthorizedSession(tenantId, userId, tokenId, sessionId);
        String keyHash = idempotencyKey == null || idempotencyKey.isBlank()
                ? null : sha256(idempotencyKey);
        String fingerprint = fingerprint(content, metadata);
        if (keyHash != null) {
            ChatTurn duplicate = turnRepository.findBySessionIdAndIdempotencyKeyHash(sessionId, keyHash)
                    .orElse(null);
            if (duplicate != null) {
                if (!duplicate.getRequestFingerprint().equals(fingerprint)) {
                    throw new ChatApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                            "The idempotency key was already used for different content.");
                }
                if (duplicate.getStatus() == ChatTurnStatus.PENDING) {
                    throw new ChatApiException(HttpStatus.CONFLICT, "MESSAGE_IN_PROGRESS",
                            "A message with this idempotency key is still being processed.");
                }
                if (duplicate.getStatus() == ChatTurnStatus.COMPLETED) {
                    ChatMessage assistant = messageRepository.findById(duplicate.getAssistantMessageId())
                            .orElseThrow();
                    return new PreparedTurn(duplicate.getId(), null, toAssistant(assistant));
                }
                duplicate.setStatus(ChatTurnStatus.PENDING);
                duplicate.setFailureCode(null);
                duplicate.setAttemptCount(duplicate.getAttemptCount() + 1);
                ChatMessage userMessage = messageRepository.findById(duplicate.getUserMessageId())
                        .orElseThrow();
                GenerationContext context = captureContext(session, duplicate, userMessage, requestId);
                duplicate.setKnowledgeRevision(context.revision());
                duplicate.setGenerationContext(context.asMap());
                return new PreparedTurn(duplicate.getId(), context, null);
            }
        }

        consumeQuota(tenantId);
        List<ChatMessage> prior = firstPriorMessages(sessionId, Integer.MAX_VALUE);
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setTenantId(tenantId);
        userMessage.setUserId(userId);
        userMessage.setRole("user");
        userMessage.setContent(content);
        userMessage.setSequenceNumber(allocateSequence(session));
        userMessage = messageRepository.save(userMessage);

        KnowledgeBase knowledgeBase = knowledgeBaseRepository
                .findByIdAndTenantId(session.getKnowledgeBaseId(), tenantId)
                .orElseThrow(() -> workspaceNotFound());
        ChatTurn turn = new ChatTurn();
        turn.setSessionId(sessionId);
        turn.setTenantId(tenantId);
        turn.setGenerationId(UUID.randomUUID());
        turn.setStatus(ChatTurnStatus.PENDING);
        turn.setIdempotencyKeyHash(keyHash);
        turn.setRequestFingerprint(fingerprint);
        turn.setUserMessageId(userMessage.getId());
        turn.setKnowledgeRevision(knowledgeBase.getSearchRevision());
        turn.setAttemptCount(1);
        turn = turnRepository.save(turn);
        GenerationContext context = captureContext(session, turn, userMessage, prior, requestId);
        turn.setGenerationContext(context.asMap());
        return new PreparedTurn(turn.getId(), context, null);
    }

    private FinalizeResult finalizeTurn(
            UUID turnId, AiInferenceClient.GeneratedAnswer answer, GenerationContext context) {
        ChatTurn turn = turnRepository.findById(turnId).orElseThrow();
        ChatSession session = sessionRepository.findForUpdate(turn.getSessionId(), turn.getTenantId())
                .orElseThrow(() -> sessionNotFound());
        KnowledgeBase knowledgeBase = knowledgeBaseRepository
                .findByIdAndTenantId(session.getKnowledgeBaseId(), session.getTenantId())
                .orElseThrow(() -> workspaceNotFound());
        if (knowledgeBase.getSearchRevision() != context.revision()) {
            return new FinalizeResult(true, null);
        }
        validateCitations(session, context, answer.citations());
        ChatMessage assistant = new ChatMessage();
        assistant.setSessionId(session.getId());
        assistant.setTenantId(session.getTenantId());
        assistant.setRole("assistant");
        assistant.setContent(answer.answer());
        assistant.setCitations(answer.citations().stream().map(this::citationMap).toList());
        assistant.setAction(answer.action() == null ? new HashMap<>() : answer.action());
        assistant.setSequenceNumber(allocateSequence(session));
        assistant = messageRepository.save(assistant);
        turn.setAssistantMessageId(assistant.getId());
        turn.setStatus(ChatTurnStatus.COMPLETED);
        turn.setFailureCode(null);
        return new FinalizeResult(false, toAssistant(assistant));
    }

    private GenerationContext rebuildContext(UUID turnId, String requestId) {
        ChatTurn turn = turnRepository.findById(turnId).orElseThrow();
        ChatSession session = sessionRepository.findForUpdate(turn.getSessionId(), turn.getTenantId())
                .orElseThrow(() -> sessionNotFound());
        ChatMessage userMessage = messageRepository.findById(turn.getUserMessageId()).orElseThrow();
        GenerationContext context = captureContext(session, turn, userMessage, requestId);
        turn.setKnowledgeRevision(context.revision());
        turn.setGenerationContext(context.asMap());
        turn.setAttemptCount(turn.getAttemptCount() + 1);
        return context;
    }

    private void markFailed(UUID turnId, String code) {
        transactions.executeWithoutResult(status -> turnRepository.findById(turnId).ifPresent(turn -> {
            if (turn.getStatus() != ChatTurnStatus.COMPLETED) {
                turn.setStatus(ChatTurnStatus.FAILED);
                turn.setFailureCode(code);
            }
        }));
    }

    private ChatDtos.SessionResponse createSession(
            UUID tenantId, UUID userId, UUID chatbotId, UUID knowledgeBaseId, String locale,
            ChatChannel channel, String externalUserId, String customerName, String customerEmail,
            Map<String, Object> metadata, UUID tokenId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> workspaceNotFound());
        if (tenant.getStatus() != TenantStatus.ACTIVE && tenant.getStatus() != TenantStatus.TRIAL) {
            throw workspaceNotFound();
        }
        Chatbot chatbot = chatbotRepository.findByIdAndTenant_IdAndKnowledgeBase_IdAndStatus(
                chatbotId, tenantId, knowledgeBaseId, ChatbotStatus.ACTIVE)
                .orElseThrow(() -> workspaceNotFound());
        if (chatbot.getKnowledgeBase().getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw workspaceNotFound();
        }
        ChatSession session = new ChatSession();
        session.setTenantId(tenant.getId());
        session.setUserId(userId);
        session.setChatbotId(chatbotId);
        session.setKnowledgeBaseId(knowledgeBaseId);
        session.setLocale(locale == null || locale.isBlank() ? "vi-VN" : locale);
        session.setChannel(channel);
        session.setExternalUserId(externalUserId);
        session.setCustomerName(customerName);
        session.setCustomerEmail(customerEmail);
        session.setCustomerMetadata(metadata == null ? new HashMap<>() : metadata);
        session.setIntegrationTokenId(tokenId);
        session = sessionRepository.save(session);
        if (channel != ChatChannel.EMPLOYEE_PLAYGROUND) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("conversationId", session.getId().toString());
            payload.put("chatbotId", chatbotId.toString());
            payload.put("channel", channel.name());
            payload.put("externalUserId", externalUserId);
            webhookService.enqueue(tenantId, "conversation.started", session.getId(), payload);
        }
        return toSession(session);
    }

    public List<ChatDtos.MessageResponse> history(
            UUID tenantId, UUID userId, UUID tokenId, UUID sessionId, int limit, int after) {
        ChatSession session = authorizedSession(tenantId, userId, tokenId, sessionId);
        return messageRepository.findBySessionIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                session.getId(), Math.max(0, after), PageRequest.of(0, Math.min(Math.max(limit, 1), 200)))
                .stream().map(this::toMessage).toList();
    }

    public void close(UUID tenantId, UUID userId, UUID tokenId, UUID sessionId) {
        transactions.executeWithoutResult(status -> {
            ChatSession session = lockedAuthorizedSession(tenantId, userId, tokenId, sessionId);
            if (session.getStatus() == ChatSessionStatus.CLOSED) {
                return;
            }
            session.setStatus(ChatSessionStatus.CLOSED);
            session.setClosedAt(LocalDateTime.now());
            if (session.getChannel() != ChatChannel.EMPLOYEE_PLAYGROUND) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("conversationId", sessionId.toString());
                payload.put("chatbotId", session.getChatbotId().toString());
                payload.put("channel", session.getChannel().name());
                payload.put("externalUserId", session.getExternalUserId());
                webhookService.enqueue(tenantId, "conversation.closed", sessionId, payload);
            }
        });
    }

    public List<ChatDtos.PlaygroundSessionResponse> playground(
            UUID tenantId, UUID userId, int limit, int offset) {
        int requested = Math.min(Math.max(limit, 1), 100);
        List<ChatSession> all = sessionRepository
                .findByTenantIdAndUserIdAndChannelAndHiddenAtIsNullOrderByLastActivityAtDesc(
                        tenantId, userId, ChatChannel.EMPLOYEE_PLAYGROUND,
                        PageRequest.of(0, Math.min(offset + requested, 1000)));
        return all.stream().skip(Math.max(offset, 0)).limit(requested).map(session -> {
            String title = messageRepository.findFirstBySessionIdAndRoleOrderBySequenceNumberAsc(
                    session.getId(), "user").map(ChatMessage::getContent).map(String::trim)
                    .filter(value -> !value.isBlank()).map(value -> value.substring(0, Math.min(60, value.length())))
                    .orElse(session.getCreatedAt().toLocalDate().toString());
            return new ChatDtos.PlaygroundSessionResponse(
                    session.getId(), title, messageRepository.countBySessionId(session.getId()),
                    session.getStatus().name(), session.getCreatedAt(), session.getLastActivityAt());
        }).toList();
    }

    public void hidePlayground(UUID tenantId, UUID userId, UUID sessionId) {
        transactions.executeWithoutResult(status -> {
            ChatSession session = sessionRepository.findForUpdate(sessionId, tenantId)
                    .orElseThrow(() -> sessionNotFound());
            if (session.getChannel() != ChatChannel.EMPLOYEE_PLAYGROUND
                    || !Objects.equals(session.getUserId(), userId)) {
                throw sessionNotFound();
            }
            session.setHiddenAt(LocalDateTime.now());
        });
    }

    public List<ChatDtos.ConversationListItemResponse> conversations(
            UUID tenantId, String status, String channel, int limit, int offset) {
        int requested = Math.min(Math.max(limit, 1), 100);
        return sessionRepository.findByTenantIdAndChannelInOrderByCreatedAtDesc(
                        tenantId, EXTERNAL_CHANNELS, PageRequest.of(0, Math.min(offset + requested, 1000)))
                .stream()
                .filter(item -> status == null || item.getStatus().name().equals(status))
                .filter(item -> channel == null || item.getChannel().name().equals(channel))
                .skip(Math.max(offset, 0)).limit(requested)
                .map(item -> new ChatDtos.ConversationListItemResponse(
                        item.getId(), item.getChannel().name(), item.getExternalUserId(),
                        item.getCustomerName(), item.getCustomerEmail(), item.getStatus().name(),
                        messageRepository.countBySessionId(item.getId()), item.getCreatedAt(),
                        item.getUpdatedAt(), item.getClosedAt())).toList();
    }

    public ChatDtos.ConversationDetailResponse conversation(UUID tenantId, UUID sessionId) {
        ChatSession session = sessionRepository.findByIdAndTenantIdAndHiddenAtIsNull(sessionId, tenantId)
                .filter(item -> EXTERNAL_CHANNELS.contains(item.getChannel()))
                .orElseThrow(() -> new ChatApiException(HttpStatus.NOT_FOUND,
                        "CONVERSATION_NOT_FOUND", "Conversation was not found."));
        return new ChatDtos.ConversationDetailResponse(
                session.getId(), session.getChannel().name(), session.getExternalUserId(),
                session.getCustomerName(), session.getCustomerEmail(), session.getCustomerMetadata(),
                session.getStatus().name(), session.getCreatedAt(), session.getUpdatedAt(),
                session.getClosedAt(), messageRepository.findBySessionIdOrderBySequenceNumberAsc(sessionId)
                .stream().map(this::toMessage).toList());
    }

    private GenerationContext captureContext(
            ChatSession session, ChatTurn turn, ChatMessage userMessage, String requestId) {
        return captureContext(session, turn, userMessage,
                firstPriorMessages(session.getId(), userMessage.getSequenceNumber()), requestId);
    }

    private GenerationContext captureContext(
            ChatSession session, ChatTurn turn, ChatMessage userMessage,
            List<ChatMessage> prior, String requestId) {
        Tenant tenant = tenantRepository.findById(session.getTenantId()).orElseThrow();
        KnowledgeBase knowledgeBase = knowledgeBaseRepository
                .findByIdAndTenantId(session.getKnowledgeBaseId(), session.getTenantId())
                .filter(item -> item.getStatus() == KnowledgeBaseStatus.ACTIVE)
                .orElseThrow(() -> workspaceNotFound());
        boolean customerVisibility = EXTERNAL_CHANNELS.contains(session.getChannel());
        List<UUID> visibleIds = customerVisibility
                ? documentRepository.findByTenantIdAndKnowledgeBaseIdAndStatusAndVisibility(
                        session.getTenantId(), session.getKnowledgeBaseId(), DocumentStatus.COMPLETED,
                        DocumentVisibility.CUSTOMER_AND_EMPLOYEE).stream().map(Document::getId).sorted().toList()
                : List.of();
        List<AiInferenceClient.PriorMessage> history = prior.stream().limit(20)
                .map(message -> new AiInferenceClient.PriorMessage(message.getRole(), message.getContent()))
                .toList();
        return new GenerationContext(
                turn.getGenerationId(), turn.getId(), session.getTenantId(), session.getChatbotId(),
                session.getKnowledgeBaseId(), knowledgeBase.getSearchRevision(), session.getChannel().name(),
                session.getLocale(), userMessage.getContent(), history, tenant.getName(),
                tenant.getCustomerAnswerPrompt(), customerVisibility, visibleIds,
                requestId == null ? UUID.randomUUID().toString() : requestId);
    }

    private List<ChatMessage> firstPriorMessages(UUID sessionId, int beforeSequence) {
        return messageRepository.findBySessionIdAndSequenceNumberLessThanOrderBySequenceNumberAsc(
                sessionId, beforeSequence, PageRequest.of(0, 20));
    }

    private void validateCitations(
            ChatSession session, GenerationContext context,
            List<ChatDtos.CitationResponse> citations) {
        if (citations.isEmpty()) {
            return;
        }
        List<UUID> citationIds;
        try {
            citationIds = citations.stream().map(ChatDtos.CitationResponse::documentId)
                    .map(UUID::fromString).distinct().toList();
        } catch (IllegalArgumentException exception) {
            throw invalidCitation();
        }
        List<Document> documents = documentRepository.findByIdInAndTenantIdAndKnowledgeBaseIdAndStatus(
                citationIds, session.getTenantId(), session.getKnowledgeBaseId(), DocumentStatus.COMPLETED);
        if (documents.size() != citationIds.size()) {
            throw invalidCitation();
        }
        if (context.customerVisibility() && documents.stream()
                .anyMatch(document -> document.getVisibility() != DocumentVisibility.CUSTOMER_AND_EMPLOYEE)) {
            throw invalidCitation();
        }
        if (context.customerVisibility() && !context.visibleDocumentIds().containsAll(citationIds)) {
            throw invalidCitation();
        }
    }

    private void consumeQuota(UUID tenantId) {
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> workspaceNotFound());
        var subscription = subscriptionRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> workspaceNotFound());
        var period = billingPeriods.currentQuotaPeriod(subscription, LocalDateTime.now());
        UsageMetrics usage = usageRepository.findByTenantIDAndPeriodStart(tenantId, period.start())
                .orElseGet(() -> {
                    UsageMetrics created = new UsageMetrics();
                    created.setTenantID(tenantId);
                    created.setPeriodYear(period.start().getYear());
                    created.setPeriodMonth(period.start().getMonthValue());
                    created.setPeriodStart(period.start());
                    created.setPeriodEnd(period.end());
                    return created;
                });
        Integer limit = tenant.getMaxMessages();
        if (limit != null && usage.getMessageCount() >= limit) {
            throw new ChatApiException(HttpStatus.TOO_MANY_REQUESTS, "MESSAGE_QUOTA_EXCEEDED",
                    "The tenant message quota has been reached.");
        }
        usage.setMessageCount(usage.getMessageCount() + 1);
        if (limit != null && usage.getMessageCount() >= Math.ceil(limit * 0.8)
                && !usage.isWarning80Sent()) {
            usage.setWarning80Sent(true);
            notification(tenantId, NotificationType.QUOTA_WARNING, "Message quota is at 80%",
                    "You have used " + usage.getMessageCount() + " of " + limit
                            + " messages in this billing period.");
        }
        if (limit != null && usage.getMessageCount() >= limit && !usage.isExceededSent()) {
            usage.setExceededSent(true);
            notification(tenantId, NotificationType.QUOTA_EXCEEDED, "Message quota reached",
                    "Additional messages are blocked until the next reset or plan renewal.");
        }
        usageRepository.save(usage);
    }

    private void notification(UUID tenantId, NotificationType type, String title, String message) {
        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    private ChatSession lockedAuthorizedSession(
            UUID tenantId, UUID userId, UUID tokenId, UUID sessionId) {
        ChatSession session = sessionRepository.findForUpdate(sessionId, tenantId)
                .orElseThrow(() -> sessionNotFound());
        authorizeSession(session, userId, tokenId);
        if (session.getStatus() != ChatSessionStatus.OPEN || session.getHiddenAt() != null) {
            throw sessionNotFound();
        }
        return session;
    }

    private ChatSession authorizedSession(UUID tenantId, UUID userId, UUID tokenId, UUID sessionId) {
        ChatSession session = sessionRepository.findByIdAndTenantIdAndHiddenAtIsNull(sessionId, tenantId)
                .orElseThrow(() -> sessionNotFound());
        authorizeSession(session, userId, tokenId);
        return session;
    }

    private void authorizeSession(ChatSession session, UUID userId, UUID tokenId) {
        if (userId != null && (session.getChannel() != ChatChannel.EMPLOYEE_PLAYGROUND
                || !userId.equals(session.getUserId()))) {
            throw sessionNotFound();
        }
        if (tokenId != null && !tokenId.equals(session.getIntegrationTokenId())) {
            throw sessionNotFound();
        }
    }

    private int allocateSequence(ChatSession session) {
        int value = session.getNextSequenceNumber();
        session.setNextSequenceNumber(value + 1);
        session.setLastActivityAt(LocalDateTime.now());
        return value;
    }

    private ChatDtos.SessionResponse toSession(ChatSession session) {
        return new ChatDtos.SessionResponse(
                session.getId().toString(), session.getChatbotId().toString(),
                session.getKnowledgeBaseId().toString(), session.getTenantId().toString(),
                session.getLocale());
    }

    private ChatDtos.AssistantMessageResponse toAssistant(ChatMessage message) {
        return new ChatDtos.AssistantMessageResponse(
                message.getRole(), message.getContent(), citations(message),
                message.getAction() == null || message.getAction().isEmpty() ? null : message.getAction());
    }

    private ChatDtos.MessageResponse toMessage(ChatMessage message) {
        return new ChatDtos.MessageResponse(
                message.getRole(), message.getContent(), citations(message), message.getSequenceNumber(),
                message.getAction() == null || message.getAction().isEmpty() ? null : message.getAction());
    }

    private List<ChatDtos.CitationResponse> citations(ChatMessage message) {
        return message.getCitations().stream().map(value -> objectMapper.convertValue(
                value, ChatDtos.CitationResponse.class)).toList();
    }

    private Map<String, Object> citationMap(ChatDtos.CitationResponse citation) {
        return objectMapper.convertValue(citation, new com.fasterxml.jackson.core.type.TypeReference<>() { });
    }

    private String fingerprint(String content, Map<String, Object> metadata) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("content", content);
        value.put("metadata", metadata == null ? Map.of() : new java.util.TreeMap<>(metadata));
        try {
            return sha256(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Message metadata is not serializable", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ChatApiException workspaceNotFound() {
        return new ChatApiException(HttpStatus.NOT_FOUND, "WORKSPACE_NOT_FOUND",
                "Chat workspace was not found.");
    }

    private ChatApiException sessionNotFound() {
        return new ChatApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                "Chat session was not found.");
    }

    private ChatApiException invalidCitation() {
        return new ChatApiException(HttpStatus.BAD_GATEWAY, "INVALID_CITATION",
                "The inference service returned an invalid citation.");
    }

    private record PreparedTurn(
            UUID turnId,
            GenerationContext context,
            ChatDtos.AssistantMessageResponse replayed
    ) {
    }

    private record FinalizeResult(
            boolean revisionChanged,
            ChatDtos.AssistantMessageResponse response
    ) {
    }

    private record GenerationContext(
            UUID generationId,
            UUID turnId,
            UUID tenantId,
            UUID chatbotId,
            UUID knowledgeBaseId,
            long revision,
            String channel,
            String locale,
            String question,
            List<AiInferenceClient.PriorMessage> priorMessages,
            String tenantName,
            String customerAnswerPrompt,
            boolean customerVisibility,
            List<UUID> visibleDocumentIds,
            String requestId
    ) {
        AiInferenceClient.GenerationRequest toRequest() {
            return new AiInferenceClient.GenerationRequest(
                    generationId, turnId, tenantId, chatbotId, knowledgeBaseId, revision,
                    channel, locale, question, priorMessages, tenantName, customerAnswerPrompt,
                    customerVisibility, visibleDocumentIds, PROMPT_SCHEMA_VERSION, requestId, requestId);
        }

        Map<String, Object> asMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("generation_id", generationId.toString());
            value.put("tenant_id", tenantId.toString());
            value.put("chatbot_id", chatbotId.toString());
            value.put("knowledge_base_id", knowledgeBaseId.toString());
            value.put("revision", revision);
            value.put("channel", channel);
            value.put("locale", locale);
            value.put("question", question);
            value.put("prior_messages", priorMessages.stream()
                    .map(message -> Map.of("role", message.role(), "content", message.content())).toList());
            value.put("tenant_name", tenantName);
            value.put("customer_answer_prompt", customerAnswerPrompt);
            value.put("customer_visibility", customerVisibility);
            value.put("visible_document_ids", visibleDocumentIds.stream().map(UUID::toString).toList());
            value.put("prompt_schema_version", PROMPT_SCHEMA_VERSION);
            return value;
        }
    }
}
