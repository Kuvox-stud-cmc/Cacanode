package com.cacanode.api.recruitment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.cacanode.api.recruitment.api.event.RecordingReadyEvent;
import com.cacanode.api.recruitment.api.event.ResumeAnalysisRequestedEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterviewEventContractTest {
    private static final Path CONTRACTS = Path.of("../contracts/ai-interview/v1");

    @Test
    void fixturesHaveExactRequiredFieldsAndCrossLanguageUuidV5Identity() throws Exception {
        Map<String, String> semanticKeys = Map.of(
                "interview.resume-analysis.requested", "requested:v1",
                "interview.resume-analysis.outcome", "completed:v1",
                "interview.turn.finalized", "turn:1",
                "interview.session.completed", "completed:v1",
                "interview.session.failed", "failed:provider-timeout",
                "interview.provider.usage", "cartesia:tts:1",
                "recruitment.recording.ready", "recording:1");
        ObjectMapper mapper = new ObjectMapper();
        try (var files = Files.list(CONTRACTS)) {
            for (Path fixture : files.filter(path -> path.toString().endsWith(".fixture.json")).toList()) {
                JsonNode payload = mapper.readTree(fixture.toFile());
                Path schemaPath = fixture.resolveSibling(
                        fixture.getFileName().toString().replace(".fixture.json", ".schema.json"));
                JsonNode schema = mapper.readTree(schemaPath.toFile());
                JsonNode requiredFields=schema.has("required")?schema.get("required"):schema.path("$defs").path("event").path("required");
                assertEquals(requiredFields.size(), payload.size(), fixture.toString());
                for (JsonNode required : requiredFields) {
                    assertEquals(true, payload.has(required.asText()), required.asText());
                }
                String eventType = payload.get("event_type").asText();
                UUID aggregateId = UUID.fromString(payload.get("aggregate_id").asText());
                String semanticKey=semanticKeys.get(eventType);
                if(payload.get("schema_version").asText().equals("1.1"))semanticKey=switch(eventType){
                    case "interview.resume-analysis.requested"->"requested:v1.1";
                    case "interview.resume-analysis.outcome"->"outcome:v1.1";
                    case "interview.turn.finalized"->"turn:"+payload.get("sequence").asInt()+":v1.1";
                    case "interview.session.completed"->"completed:v1.1";
                    case "interview.session.failed"->"failed:v1.1";
                    case "interview.provider.usage"->payload.get("provider").asText().toLowerCase()+":"+
                            payload.get("capability").asText().toLowerCase()+":v1.1";
                    default->throw new IllegalStateException(eventType);};
                assertEquals(
                        InterviewEventIdentity.eventId(eventType, aggregateId, semanticKey),
                        UUID.fromString(payload.get("event_id").asText()));
            }
        }
    }

    @Test
    void javaOwnedProducerAndRecordingFactsDeserializeFromSharedFixtures() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        ResumeAnalysisRequestedEvent request = mapper.readValue(
                CONTRACTS.resolve("resume-analysis-request.fixture.json").toFile(),
                ResumeAnalysisRequestedEvent.class);
        RecordingReadyEvent recording = mapper.readValue(
                CONTRACTS.resolve("recording-ready.fixture.json").toFile(),
                RecordingReadyEvent.class);
        assertEquals("interview.resume-analysis.requested", request.eventType());
        assertEquals(request.analysisId(), request.aggregateId());
        assertEquals("recruitment.recording.ready", recording.eventType());
        assertEquals(recording.sessionId(), recording.aggregateId());
    }

    @Test
    void cvAnalysisIdentityMatchesCrossLanguageFixture() throws Exception {
        JsonNode payload=new ObjectMapper().readTree(
                CONTRACTS.resolve("resume-analysis-request-v1.1.fixture.json").toFile());
        assertEquals(UUID.fromString(payload.get("analysis_id").asText()),InterviewEventIdentity.resumeAnalysisId(
                UUID.fromString(payload.get("tenant_id").asText()),
                UUID.fromString(payload.get("application_id").asText()),payload.get("cv_sha256").asText(),
                payload.get("analysis_mode").asText(),payload.get("policy_version").asText(),
                payload.get("model_version").asText()));
    }
}
