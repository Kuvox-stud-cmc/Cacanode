package com.cacanode.api.ai.infrastructure;

import com.cacanode.ai.v1.GenerateAnswerResponse;
import com.cacanode.ai.v1.TicketDraft;
import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.ai.api.InterviewInferenceException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcAiInferenceClientTest {

    @Test
    void mapsTicketDraftToPublicActionContract() {
        GenerateAnswerResponse response = GenerateAnswerResponse.newBuilder()
                .setTicketDraft(TicketDraft.newBuilder()
                        .setTitle("Duplicate payment")
                        .setDescription("Customer was charged twice.")
                        .setCustomerEmail("customer@example.com")
                        .putMetadata("priority", "urgent"))
                .build();

        var action = GrpcAiInferenceClient.ticketDraftAction(response);

        assertEquals("ticket_draft", action.get("type"));
        assertEquals("Duplicate payment", action.get("title"));
        assertEquals("Customer was charged twice.", action.get("description"));
        assertEquals("customer@example.com", action.get("customer_email"));
        assertEquals("urgent", ((java.util.Map<?, ?>) action.get("metadata")).get("priority"));
    }

    @Test
    void returnsNoActionWithoutTicketDraft() {
        assertNull(GrpcAiInferenceClient.ticketDraftAction(
                GenerateAnswerResponse.getDefaultInstance()));
    }

    @Test
    void mapsImmutableInterviewSnapshotAndResponse() {
        UUID sessionId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID attemptId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        var command = new InterviewInferenceApi.PrepareInterviewCommand(
                sessionId,
                attemptId,
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                UUID.fromString("99999999-9999-4999-8999-999999999999"),
                "v1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "Cacanode",
                "Candidate",
                "Introduction",
                "Disclosure",
                "Closing",
                1200,
                new InterviewInferenceApi.InteractionLimits(1, 2, 10, 2),
                false,
                true,
                List.of(new InterviewInferenceApi.SectionSnapshot(
                        UUID.fromString("77777777-7777-4777-8777-777777777777"),
                        1,
                        InterviewInferenceApi.SectionKind.CORE,
                        "vi-VN",
                        900,
                        "",
                        List.of(new InterviewInferenceApi.QuestionSnapshot(
                                UUID.fromString("66666666-6666-4666-8666-666666666666"),
                                1,
                                "Tell us about your experience.",
                                "experience",
                                "Evidence and clarity",
                                1,
                                InterviewInferenceApi.QuestionSource.CV_PERSONALIZED,
                                "Built event-driven services")))),
                new InterviewInferenceApi.Trace("request", "trace", "parent", Map.of("tenant", "a")));

        var request = GrpcAiInferenceClient.prepareRequest(command);
        assertEquals(sessionId.toString(), request.getSessionId());
        assertEquals("vi-VN", request.getSections(0).getLanguageTag());
        assertEquals("Built event-driven services",
                request.getSections(0).getQuestions(0).getEvidence());
        assertEquals("a", request.getTrace().getBaggageMap().get("tenant"));

        var mapped = GrpcAiInferenceClient.preparedInterview(command,
                com.cacanode.ai.v1.PrepareInterviewSessionResponse.newBuilder()
                        .setSessionId(sessionId.toString())
                        .setCallAttemptId(attemptId.toString())
                        .setRuntimeToken("opaque")
                        .setExpiresAtEpochSeconds(Instant.parse("2026-07-23T09:00:00Z").getEpochSecond())
                        .setAcceptedSnapshotSha256(command.snapshotSha256())
                        .build());
        assertEquals("opaque", mapped.runtimeToken());
        assertEquals(Instant.parse("2026-07-23T09:00:00Z"), mapped.expiresAt());
    }

    @Test
    void rejectsMismatchedPreparedInterviewResponse() {
        var command = new InterviewInferenceApi.PrepareInterviewCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "v1", "hash", "company", "candidate", "intro", "disclosure", "closing", 60,
                new InterviewInferenceApi.InteractionLimits(1, 1, 5, 1),
                false, false, List.of(), null);
        assertThrows(InterviewInferenceException.class, () ->
                GrpcAiInferenceClient.preparedInterview(command,
                        com.cacanode.ai.v1.PrepareInterviewSessionResponse.getDefaultInstance()));
    }
}
