package com.cacanode.api.ai.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface InterviewInferenceApi {
    PreparedInterview prepare(PrepareInterviewCommand command);

    CancelledInterview cancel(CancelInterviewCommand command);

    enum SectionKind {
        CORE,
        ENGLISH_SCREEN
    }

    enum QuestionSource {
        TEMPLATE,
        CV_PERSONALIZED
    }

    record QuestionSnapshot(
            UUID questionId,
            int position,
            String prompt,
            String competency,
            String rubric,
            int followUpLimit,
            QuestionSource source,
            String evidence) {
    }

    record SectionSnapshot(
            UUID sectionId,
            int position,
            SectionKind kind,
            String languageTag,
            int durationLimitSeconds,
            String transitionText,
            List<QuestionSnapshot> questions) {
        public SectionSnapshot {
            questions = List.copyOf(questions);
        }
    }

    record InteractionLimits(
            int repetitionLimit,
            int clarificationLimit,
            int silenceTimeoutSeconds,
            int silencePromptLimit) {
    }

    record Trace(String requestId, String traceId, String parentSpanId, Map<String, String> baggage) {
        public Trace {
            baggage = baggage == null ? Map.of() : Map.copyOf(baggage);
        }
    }

    record PrepareInterviewCommand(
            UUID sessionId,
            UUID callAttemptId,
            UUID tenantId,
            UUID templateRevisionId,
            String snapshotVersion,
            String snapshotSha256,
            String companyDisplayName,
            String candidateDisplayName,
            String introductionText,
            String disclosureText,
            String closingText,
            int durationLimitSeconds,
            InteractionLimits interactionLimits,
            boolean recordingEnabled,
            boolean cvPersonalizationEnabled,
            List<SectionSnapshot> sections,
            Trace trace) {
        public PrepareInterviewCommand {
            sections = List.copyOf(sections);
        }
    }

    record PreparedInterview(
            UUID sessionId,
            UUID callAttemptId,
            String runtimeToken,
            Instant expiresAt,
            String acceptedSnapshotSha256) {
    }

    record CancelInterviewCommand(
            UUID sessionId, UUID callAttemptId, String reason, Trace trace) {
    }

    record CancelledInterview(
            UUID sessionId, UUID callAttemptId, boolean cancelled, boolean alreadyTerminal) {
    }
}
