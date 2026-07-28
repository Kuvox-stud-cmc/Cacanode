package com.cacanode.api.ai;

import com.cacanode.ai.v1.CacanodeAiProto;
import com.google.protobuf.Descriptors;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtobufContractTest {
    @Test
    void existingMethodsRemainOrderedAndInterviewMethodsAreAdditive() {
        var service = CacanodeAiProto.getDescriptor().findServiceByName("InferenceService");
        assertEquals(List.of(
                "GenerateAnswer",
                "ListDocumentUnits",
                "DeleteDocumentIndex",
                "PrepareInterviewSession",
                "CancelInterviewSession"),
                service.getMethods().stream().map(Descriptors.MethodDescriptor::getName).toList());
    }

    @Test
    void prepareInterviewFieldsAndEnumsAreFrozen() {
        assertFields("PrepareInterviewSessionRequest", Map.ofEntries(
                Map.entry("session_id", 1), Map.entry("call_attempt_id", 2),
                Map.entry("tenant_id", 3), Map.entry("template_revision_id", 4),
                Map.entry("snapshot_version", 5), Map.entry("snapshot_sha256", 6),
                Map.entry("company_display_name", 7), Map.entry("candidate_display_name", 8),
                Map.entry("introduction_text", 9), Map.entry("disclosure_text", 10),
                Map.entry("closing_text", 11), Map.entry("duration_limit_seconds", 12),
                Map.entry("interaction_limits", 13), Map.entry("recording_enabled", 14),
                Map.entry("cv_personalization_enabled", 15), Map.entry("sections", 16),
                Map.entry("trace", 17)));
        assertFields("PrepareInterviewSessionResponse", Map.of(
                "session_id", 1, "call_attempt_id", 2, "runtime_token", 3,
                "expires_at_epoch_seconds", 4, "accepted_snapshot_sha256", 5));
        assertFields("CancelInterviewSessionRequest", Map.of(
                "session_id", 1, "call_attempt_id", 2, "reason", 3, "trace", 4));
        assertFields("CancelInterviewSessionResponse", Map.of(
                "session_id", 1, "call_attempt_id", 2, "cancelled", 3,
                "already_terminal", 4));
        var question = CacanodeAiProto.getDescriptor()
                .findMessageTypeByName("InterviewQuestionSnapshot");
        assertTrue(question.findFieldByName("evidence").hasPresence());
        assertEquals(1, CacanodeAiProto.getDescriptor()
                .findEnumTypeByName("InterviewSectionKind").findValueByName(
                        "INTERVIEW_SECTION_KIND_CORE").getNumber());
        assertEquals(2, CacanodeAiProto.getDescriptor()
                .findEnumTypeByName("InterviewQuestionSource").findValueByName(
                        "INTERVIEW_QUESTION_SOURCE_CV_PERSONALIZED").getNumber());
    }

    private void assertFields(String messageName, Map<String, Integer> expected) {
        var descriptor = CacanodeAiProto.getDescriptor().findMessageTypeByName(messageName);
        Map<String, Integer> actual = new LinkedHashMap<>();
        descriptor.getFields().forEach(field -> actual.put(field.getName(), field.getNumber()));
        assertEquals(expected, actual);
    }
}
