package com.cacanode.api.recruitment.api.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResumeAnalysisOutcomeEvent(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("aggregate_id") UUID aggregateId,
        @JsonProperty("analysis_id") UUID analysisId,
        @JsonProperty("application_id") UUID applicationId,
        @JsonProperty("cv_sha256") String cvSha256,
        @JsonProperty("analysis_mode") String analysisMode,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("model_version") String modelVersion,
        String status,
        String summary,
        List<Evidence> evidence,
        List<Skill> skills,
        @JsonProperty("personalized_questions") List<PersonalizedQuestion> personalizedQuestions,
        @JsonProperty("error_code") String errorCode) {
    public record Evidence(@JsonProperty("anchor_id") String anchorId,String excerpt,
            @JsonProperty("source_location") String sourceLocation) {}
    public record Skill(String name,@JsonProperty("evidence_anchor_ids") List<String> evidenceAnchorIds) {}
    public record PersonalizedQuestion(
            @JsonProperty("question_id") UUID questionId,
            @JsonProperty("target_section_id") UUID targetSectionId,
            String prompt,String competency,String rubric,
            @JsonProperty("evidence_anchor_ids") List<String> evidenceAnchorIds) {}
}
