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
        @JsonProperty("analysis_revision") Integer analysisRevision,
        String status,
        String summary,
        List<Evidence> evidence,
        List<Skill> skills,
        @JsonProperty("personalized_questions") List<PersonalizedQuestion> personalizedQuestions,
        @JsonProperty("fit_score_percent") Integer fitScorePercent,
        @JsonProperty("fit_confidence") String fitConfidence,
        @JsonProperty("fit_explanation") String fitExplanation,
        List<FitFinding> strengths,
        List<FitFinding> gaps,
        @JsonProperty("error_code") String errorCode) {
    public record Evidence(@JsonProperty("anchor_id") String anchorId,String excerpt,
            @JsonProperty("source_location") String sourceLocation) {}
    public record Skill(String name,@JsonProperty("evidence_anchor_ids") List<String> evidenceAnchorIds) {}
    public record PersonalizedQuestion(
            @JsonProperty("question_id") UUID questionId,
            @JsonProperty("target_section_id") UUID targetSectionId,
            String prompt,String competency,String rubric,
            @JsonProperty("evidence_anchor_ids") List<String> evidenceAnchorIds) {}
    public record FitFinding(
            @JsonProperty("weight_percent") int weightPercent,
            @JsonProperty("match_percent") int matchPercent,
            @JsonProperty("evidence_status") String evidenceStatus,
            String explanation,
            @JsonProperty("job_excerpt") String jobExcerpt,
            @JsonProperty("job_anchor_id") String jobAnchorId,
            @JsonProperty("cv_evidence_anchor_ids") List<String> cvEvidenceAnchorIds) {}
}
