package com.cacanode.api.ai.infrastructure;

import com.cacanode.api.ai.api.AiInferenceApi;
import com.cacanode.api.ai.api.AiInferenceException;
import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.ai.api.InterviewInferenceException;

import com.cacanode.ai.v1.DeleteDocumentIndexRequest;
import com.cacanode.ai.v1.GenerateAnswerRequest;
import com.cacanode.ai.v1.GenerateAnswerResponse;
import com.cacanode.ai.v1.InferenceServiceGrpc;
import com.cacanode.ai.v1.ListDocumentUnitsRequest;
import com.cacanode.ai.v1.TraceMetadata;
import com.cacanode.ai.v1.VisibilityMode;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.File;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class GrpcAiInferenceClient implements AiInferenceApi, InterviewInferenceApi {
    private final ManagedChannel channel;
    private final InferenceServiceGrpc.InferenceServiceBlockingStub stub;
    private final long answerDeadlineSeconds;
    private final long unitDeadlineSeconds;
    private final long deletionDeadlineSeconds;
    private final long interviewDeadlineSeconds;

    public GrpcAiInferenceClient(
            @Value("${app.ai.grpc.target:localhost:50051}") String target,
            @Value("${app.ai.grpc.plaintext:true}") boolean plaintext,
            @Value("${app.ai.grpc.ca-certificate:}") String caCertificate,
            @Value("${app.ai.grpc.client-certificate:}") String clientCertificate,
            @Value("${app.ai.grpc.client-key:}") String clientKey,
            @Value("${app.ai.grpc.authority-override:}") String authorityOverride,
            @Value("${app.ai.grpc.answer-deadline-seconds:100}") long answerDeadlineSeconds,
            @Value("${app.ai.grpc.document-units-deadline-seconds:10}") long unitDeadlineSeconds,
            @Value("${app.ai.grpc.deletion-deadline-seconds:15}") long deletionDeadlineSeconds,
            @Value("${app.ai.grpc.interview-deadline-seconds:10}") long interviewDeadlineSeconds
    ) {
        NettyChannelBuilder builder = NettyChannelBuilder.forTarget(target);
        if (plaintext) {
            builder.usePlaintext();
        } else {
            if (caCertificate.isBlank() || clientCertificate.isBlank() || clientKey.isBlank()) {
                throw new IllegalStateException("Production AI gRPC mTLS material is incomplete");
            }
            try {
                builder.sslContext(GrpcSslContexts.forClient()
                        .trustManager(new File(caCertificate))
                        .keyManager(new File(clientCertificate), new File(clientKey))
                        .build());
            } catch (SSLException exception) {
                throw new IllegalStateException("Unable to configure AI gRPC mTLS", exception);
            }
        }
        if (!authorityOverride.isBlank()) {
            builder.overrideAuthority(authorityOverride);
        }
        this.channel = builder.maxInboundMessageSize(16 * 1024 * 1024).build();
        this.stub = InferenceServiceGrpc.newBlockingStub(channel);
        this.answerDeadlineSeconds = answerDeadlineSeconds;
        this.unitDeadlineSeconds = unitDeadlineSeconds;
        this.deletionDeadlineSeconds = deletionDeadlineSeconds;
        this.interviewDeadlineSeconds = interviewDeadlineSeconds;
    }

    @Override
    public GeneratedAnswer generate(GenerationRequest request) {
        GenerateAnswerRequest.Builder builder = GenerateAnswerRequest.newBuilder()
                .setGenerationId(request.generationId().toString())
                .setTurnId(request.turnId().toString())
                .setTenantId(request.tenantId().toString())
                .setChatbotId(request.chatbotId().toString())
                .setKnowledgeBaseId(request.knowledgeBaseId().toString())
                .setAuthoritativeRevision(request.authoritativeRevision())
                .setChannel(request.channel())
                .setLocale(request.locale())
                .setQuestion(request.question())
                .setTenantName(request.tenantName())
                .setCustomerAnswerPrompt(request.customerAnswerPrompt())
                .setVisibilityMode(request.customerVisibility()
                        ? VisibilityMode.CUSTOMER_VISIBLE_DOCUMENTS
                        : VisibilityMode.ALL_TENANT_DOCUMENTS)
                .setPromptSchemaVersion(request.promptSchemaVersion())
                .setTrace(trace(request.requestId(), request.traceId()));
        request.priorMessages().stream().limit(20).forEach(message -> builder.addPriorMessages(
                com.cacanode.ai.v1.PriorMessage.newBuilder()
                        .setRole(message.role()).setContent(message.content())));
        request.visibleDocumentIds().stream().map(UUID::toString).sorted()
                .forEach(builder::addVisibleDocumentIds);

        GenerateAnswerResponse response = unavailableRetry(
                answerDeadlineSeconds,
                service -> service.generateAnswer(builder.build()),
                "answer generation");
        if (!response.getGenerationId().equals(request.generationId().toString())
                || response.getAuthoritativeRevision() != request.authoritativeRevision()) {
            throw new AiInferenceException(HttpStatus.BAD_GATEWAY, "INVALID_AI_RESPONSE",
                    "The inference service returned mismatched generation context.");
        }
        Map<String, Object> action = ticketDraftAction(response);
        return new GeneratedAnswer(
                UUID.fromString(response.getGenerationId()),
                response.getAuthoritativeRevision(),
                response.getAnswer(),
                response.getCitationsList().stream().map(this::citation).toList(),
                action,
                response.hasInputTokens() ? response.getInputTokens() : null,
                response.hasOutputTokens() ? response.getOutputTokens() : null,
                response.getCacheTier(),
                response.hasAvoidedInputTokens() ? response.getAvoidedInputTokens() : null,
                response.hasAvoidedOutputTokens() ? response.getAvoidedOutputTokens() : null);
    }

    static Map<String, Object> ticketDraftAction(GenerateAnswerResponse response) {
        if (!response.hasTicketDraft()) {
            return null;
        }
        Map<String, Object> action = new HashMap<>();
        action.put("type", "ticket_draft");
        action.put("title", response.getTicketDraft().getTitle());
        action.put("description", response.getTicketDraft().getDescription());
        action.put("customer_email", response.getTicketDraft().getCustomerEmail());
        action.put("metadata", response.getTicketDraft().getMetadataMap());
        return action;
    }

    @Override
    public List<AiInferenceApi.DocumentUnit> listDocumentUnits(
            UUID tenantId, UUID knowledgeBaseId, UUID documentId, String requestId) {
        var request = ListDocumentUnitsRequest.newBuilder()
                .setTenantId(tenantId.toString())
                .setKnowledgeBaseId(knowledgeBaseId.toString())
                .setDocumentId(documentId.toString())
                .setTrace(trace(requestId, requestId))
                .build();
        return unavailableRetry(unitDeadlineSeconds, service -> service.listDocumentUnits(request),
                "document-unit read").getUnitsList().stream().map(this::documentUnit).toList();
    }

    @Override
    public void deleteDocumentIndex(
            UUID tenantId, UUID knowledgeBaseId, UUID documentId, String requestId) {
        var request = DeleteDocumentIndexRequest.newBuilder()
                .setTenantId(tenantId.toString())
                .setKnowledgeBaseId(knowledgeBaseId.toString())
                .setDocumentId(documentId.toString())
                .setTrace(trace(requestId, requestId))
                .build();
        unavailableRetry(deletionDeadlineSeconds, service -> service.deleteDocumentIndex(request),
                "document-index deletion");
    }

    @Override
    public PreparedInterview prepare(PrepareInterviewCommand command) {
        var response = interviewCall(
                service -> service.prepareInterviewSession(prepareRequest(command)),
                "prepare interview session");
        return preparedInterview(command, response);
    }

    @Override
    public CancelledInterview cancel(CancelInterviewCommand command) {
        var request = com.cacanode.ai.v1.CancelInterviewSessionRequest.newBuilder()
                .setSessionId(command.sessionId().toString())
                .setCallAttemptId(command.callAttemptId().toString())
                .setReason(command.reason())
                .setTrace(interviewTrace(command.trace()))
                .build();
        var response = interviewCall(
                service -> service.cancelInterviewSession(request),
                "cancel interview session");
        if (!response.getSessionId().equals(command.sessionId().toString())
                || !response.getCallAttemptId().equals(command.callAttemptId().toString())) {
            throw new InterviewInferenceException(
                    "INVALID_INTERVIEW_RESPONSE", "Interview runtime returned mismatched identifiers.");
        }
        return new CancelledInterview(
                UUID.fromString(response.getSessionId()),
                UUID.fromString(response.getCallAttemptId()),
                response.getCancelled(),
                response.getAlreadyTerminal());
    }

    static com.cacanode.ai.v1.PrepareInterviewSessionRequest prepareRequest(
            PrepareInterviewCommand command) {
        var limits = command.interactionLimits();
        var builder = com.cacanode.ai.v1.PrepareInterviewSessionRequest.newBuilder()
                .setSessionId(command.sessionId().toString())
                .setCallAttemptId(command.callAttemptId().toString())
                .setTenantId(command.tenantId().toString())
                .setTemplateRevisionId(command.templateRevisionId().toString())
                .setSnapshotVersion(command.snapshotVersion())
                .setSnapshotSha256(command.snapshotSha256())
                .setCompanyDisplayName(command.companyDisplayName())
                .setCandidateDisplayName(command.candidateDisplayName())
                .setIntroductionText(command.introductionText())
                .setDisclosureText(command.disclosureText())
                .setClosingText(command.closingText())
                .setDurationLimitSeconds(command.durationLimitSeconds())
                .setInteractionLimits(com.cacanode.ai.v1.InterviewInteractionLimits.newBuilder()
                        .setRepetitionLimit(limits.repetitionLimit())
                        .setClarificationLimit(limits.clarificationLimit())
                        .setSilenceTimeoutSeconds(limits.silenceTimeoutSeconds())
                        .setSilencePromptLimit(limits.silencePromptLimit()))
                .setRecordingEnabled(command.recordingEnabled())
                .setCvPersonalizationEnabled(command.cvPersonalizationEnabled())
                .setTrace(interviewTrace(command.trace()));
        command.sections().stream().sorted(java.util.Comparator.comparingInt(SectionSnapshot::position))
                .map(GrpcAiInferenceClient::section).forEach(builder::addSections);
        return builder.build();
    }

    static PreparedInterview preparedInterview(
            PrepareInterviewCommand command,
            com.cacanode.ai.v1.PrepareInterviewSessionResponse response) {
        if (!response.getSessionId().equals(command.sessionId().toString())
                || !response.getCallAttemptId().equals(command.callAttemptId().toString())
                || !response.getAcceptedSnapshotSha256().equals(command.snapshotSha256())
                || response.getRuntimeToken().isBlank()) {
            throw new InterviewInferenceException(
                    "INVALID_INTERVIEW_RESPONSE", "Interview runtime rejected the prepared snapshot context.");
        }
        return new PreparedInterview(
                UUID.fromString(response.getSessionId()),
                UUID.fromString(response.getCallAttemptId()),
                response.getRuntimeToken(),
                Instant.ofEpochSecond(response.getExpiresAtEpochSeconds()),
                response.getAcceptedSnapshotSha256());
    }

    private static com.cacanode.ai.v1.InterviewSectionSnapshot section(SectionSnapshot section) {
        var builder = com.cacanode.ai.v1.InterviewSectionSnapshot.newBuilder()
                .setSectionId(section.sectionId().toString())
                .setPosition(section.position())
                .setKind(section.kind() == SectionKind.ENGLISH_SCREEN
                        ? com.cacanode.ai.v1.InterviewSectionKind.INTERVIEW_SECTION_KIND_ENGLISH_SCREEN
                        : com.cacanode.ai.v1.InterviewSectionKind.INTERVIEW_SECTION_KIND_CORE)
                .setLanguageTag(section.languageTag())
                .setDurationLimitSeconds(section.durationLimitSeconds())
                .setTransitionText(section.transitionText());
        section.questions().stream().sorted(java.util.Comparator.comparingInt(QuestionSnapshot::position))
                .map(GrpcAiInferenceClient::question).forEach(builder::addQuestions);
        return builder.build();
    }

    private static com.cacanode.ai.v1.InterviewQuestionSnapshot question(QuestionSnapshot question) {
        var builder = com.cacanode.ai.v1.InterviewQuestionSnapshot.newBuilder()
                .setQuestionId(question.questionId().toString())
                .setPosition(question.position())
                .setPrompt(question.prompt())
                .setCompetency(question.competency())
                .setRubric(question.rubric())
                .setFollowUpLimit(question.followUpLimit())
                .setSource(question.source() == QuestionSource.CV_PERSONALIZED
                        ? com.cacanode.ai.v1.InterviewQuestionSource.INTERVIEW_QUESTION_SOURCE_CV_PERSONALIZED
                        : com.cacanode.ai.v1.InterviewQuestionSource.INTERVIEW_QUESTION_SOURCE_TEMPLATE);
        if (question.evidence() != null) {
            builder.setEvidence(question.evidence());
        }
        return builder.build();
    }

    private static TraceMetadata interviewTrace(Trace trace) {
        if (trace == null) {
            return TraceMetadata.getDefaultInstance();
        }
        return TraceMetadata.newBuilder()
                .setRequestId(trace.requestId() == null ? "" : trace.requestId())
                .setTraceId(trace.traceId() == null ? "" : trace.traceId())
                .setParentSpanId(trace.parentSpanId() == null ? "" : trace.parentSpanId())
                .putAllBaggage(trace.baggage())
                .build();
    }

    private <T> T interviewCall(
            Function<InferenceServiceGrpc.InferenceServiceBlockingStub, T> operation,
            String operationName) {
        try {
            return operation.apply(stub.withDeadlineAfter(interviewDeadlineSeconds, TimeUnit.SECONDS));
        } catch (StatusRuntimeException exception) {
            Status.Code status = exception.getStatus().getCode();
            String details = exception.getStatus().getDescription();
            if (status == Status.Code.FAILED_PRECONDITION && "INTERVIEW_DISABLED".equals(details)) {
                throw new InterviewInferenceException("INTERVIEW_DISABLED", "Interview runtime is disabled.");
            }
            if (status == Status.Code.UNAVAILABLE
                    && "INTERVIEW_RUNTIME_NOT_READY".equals(details)) {
                throw new InterviewInferenceException(
                        "INTERVIEW_RUNTIME_NOT_READY", "Interview runtime is not ready.");
            }
            if (status == Status.Code.DEADLINE_EXCEEDED) {
                throw new InterviewInferenceException(
                        "INTERVIEW_RUNTIME_TIMEOUT", "Interview runtime request timed out.");
            }
            throw new InterviewInferenceException(
                    "INTERVIEW_RUNTIME_ERROR", "The inference service could not " + operationName + ".");
        }
    }

    private <T> T unavailableRetry(
            long deadlineSeconds,
            Function<InferenceServiceGrpc.InferenceServiceBlockingStub, T> operation,
            String operationName) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return operation.apply(stub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS));
            } catch (StatusRuntimeException exception) {
                if (exception.getStatus().getCode() == Status.Code.UNAVAILABLE && attempt == 0) {
                    continue;
                }
                if (exception.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                    throw new AiInferenceException(HttpStatus.GATEWAY_TIMEOUT, "MODEL_TIMEOUT",
                            "The model took too long to answer.");
                }
                if (exception.getStatus().getCode() == Status.Code.NOT_FOUND) {
                    throw new AiInferenceException(HttpStatus.NOT_FOUND, "INDEXED_DOCUMENT_NOT_FOUND",
                            "Indexed document was not found.");
                }
                throw new AiInferenceException(HttpStatus.BAD_GATEWAY, "MODEL_PROVIDER_ERROR",
                        "The inference service could not complete " + operationName + ".");
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private TraceMetadata trace(String requestId, String traceId) {
        return TraceMetadata.newBuilder()
                .setRequestId(requestId == null ? "" : requestId)
                .setTraceId(traceId == null ? "" : traceId)
                .build();
    }

    private AiInferenceApi.Citation citation(com.cacanode.ai.v1.Citation citation) {
        return new AiInferenceApi.Citation(
                citation.getId(), citation.getDocumentId(), citation.getSourceName(),
                citation.hasPageNumber() ? citation.getPageNumber() : null,
                citation.getChunkIndex(), citation.getScore(), citation.getSnippet(),
                citation.hasUnitId() ? citation.getUnitId() : null,
                citation.hasModality() ? citation.getModality() : null,
                citation.getSectionPathList(),
                citation.hasBlockType() ? citation.getBlockType() : null,
                citation.hasSheetName() ? citation.getSheetName() : null,
                citation.hasCellRange() ? citation.getCellRange() : null,
                citation.hasTableId() ? citation.getTableId() : null,
                null);
    }

    private AiInferenceApi.DocumentUnit documentUnit(com.cacanode.ai.v1.DocumentUnit unit) {
        return new AiInferenceApi.DocumentUnit(
                unit.hasUnitId() ? unit.getUnitId() : null,
                unit.getChunkIndex(), unit.getText(),
                unit.hasSourceName() ? unit.getSourceName() : null,
                unit.hasModality() ? unit.getModality() : null,
                unit.hasBlockType() ? unit.getBlockType() : null,
                unit.getSectionPathList(),
                unit.hasHeadingContext() ? unit.getHeadingContext() : null,
                unit.hasPageNumber() ? unit.getPageNumber() : null,
                unit.hasSheetName() ? unit.getSheetName() : null,
                unit.hasCellRange() ? unit.getCellRange() : null,
                unit.hasTableId() ? unit.getTableId() : null,
                unit.hasSourceStart() ? unit.getSourceStart() : null,
                unit.hasSourceEnd() ? unit.getSourceEnd() : null);
    }

    @PreDestroy
    void close() throws InterruptedException {
        channel.shutdown();
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            channel.shutdownNow();
        }
    }
}
