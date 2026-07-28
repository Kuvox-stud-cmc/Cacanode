package com.cacanode.api.recruitment.api.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResumeAnalysisRequestedEvent(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("aggregate_id") UUID aggregateId,
        @JsonProperty("analysis_id") UUID analysisId,
        @JsonProperty("application_id") UUID applicationId,
        @JsonProperty("document_id") UUID documentId,
        @JsonProperty("storage_key") String storageKey,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("file_size_bytes") long fileSizeBytes,
        @JsonProperty("requested_language_tag") String requestedLanguageTag,
        @JsonProperty("cv_sha256") String cvSha256,
        @JsonProperty("analysis_mode") String analysisMode,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("analysis_revision") Integer analysisRevision,
        @JsonProperty("job_title") String jobTitle,
        @JsonProperty("job_description") String jobDescription,
        @JsonProperty("job_context_anchors") List<JobContextAnchor> jobContextAnchors,
        @JsonProperty("job_context_truncated") Boolean jobContextTruncated,
        @JsonProperty("allowed_core_section_ids") List<UUID> allowedCoreSectionIds,
        @JsonProperty("template_questions") List<TemplateQuestion> templateQuestions,
        @JsonProperty("personalized_question_limit") int personalizedQuestionLimit) {
    public record TemplateQuestion(
            @JsonProperty("question_id") UUID questionId,
            @JsonProperty("section_id") UUID sectionId,
            String prompt,
            String competency) {}
    public record JobContextAnchor(
            @JsonProperty("anchor_id") String anchorId,
            String field,
            String excerpt) {}
}
