package com.cacanode.api.recruitment.service;

import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InterviewSessionSnapshotFactoryTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final InterviewSessionSnapshotFactory factory=new InterviewSessionSnapshotFactory(mapper);

    @Test void appendsOnlyGroundedCompletedPersonalizedQuestionsAndHashesDetachedNfcJson() throws Exception {
        UUID sectionId=UUID.randomUUID(),attemptId=UUID.randomUUID();
        RecruitmentInterview interview=interview(sectionId);RecruitmentJob job=job();RecruitmentCandidate candidate=candidate();
        RecruitmentCvAnalysis analysis=new RecruitmentCvAnalysis();analysis.setId(UUID.randomUUID());
        analysis.setStatus(CvAnalysisRecordStatus.COMPLETED);analysis.setAnalysisMode(CvAiMode.PERSONALIZED_QUESTIONS);
        analysis.setOutcomePayloadSha256("a".repeat(64));
        analysis.setEvidence("[{\"anchor_id\":\"linked\",\"excerpt\":\"Built a resilient Java service\",\"source_location\":\"page 1\"},"+
                "{\"anchor_id\":\"unused\",\"excerpt\":\"Private unrelated evidence\",\"source_location\":\"page 2\"}]");
        analysis.setPersonalizedQuestions("[{\"question_id\":\""+UUID.randomUUID()+"\",\"target_section_id\":\""+
                sectionId+"\",\"prompt\":\"How did you handle retries?\",\"competency\":\"Reliability\",\"rubric\":\"Look for idempotency\","+
                "\"evidence_anchor_ids\":[\"linked\"]}]");

        var first=factory.build(interview,job,candidate,analysis,attemptId);
        var replay=factory.build(interview,job,candidate,analysis,attemptId);
        assertEquals(first,replay);assertTrue(first.cvPersonalizationEnabled());
        var root=mapper.readTree(first.json());assertFalse(root.path("recordingEnabled").asBoolean());
        assertEquals(2,root.path("sections").get(0).path("questions").size());
        String json=first.json();assertTrue(json.contains("Built a resilient Java service"));
        assertFalse(json.contains("Private unrelated evidence"));assertFalse(json.contains("source_location"));
        assertEquals(first.sha256(),root.path("snapshotSha256").asText());
        ((com.fasterxml.jackson.databind.node.ObjectNode)root).remove("snapshotSha256");
        assertEquals(first.sha256(),sha256(factory.canonical(root)));
    }

    @Test void failedOrSummaryOnlyAnalysisUsesTemplateOnlyContent() throws Exception {
        UUID sectionId=UUID.randomUUID();RecruitmentCvAnalysis analysis=new RecruitmentCvAnalysis();
        analysis.setStatus(CvAnalysisRecordStatus.FAILED);analysis.setAnalysisMode(CvAiMode.PERSONALIZED_QUESTIONS);
        analysis.setEvidence("[]");analysis.setPersonalizedQuestions("[]");
        var snapshot=factory.build(interview(sectionId),job(),candidate(),analysis,UUID.randomUUID());
        var root=mapper.readTree(snapshot.json());assertFalse(snapshot.cvPersonalizationEnabled());
        assertEquals(1,root.path("sections").get(0).path("questions").size());
        assertTrue(root.path("disclosureText").asText().contains("not used"));
    }

    @Test void canonicalJsonSortsEveryObjectForCrossLanguageHashing()throws Exception{
        var root=mapper.readTree("{\"z\":1,\"a\":{\"y\":2,\"b\":3},\"items\":[{\"d\":4,\"c\":5}]}");
        assertEquals("{\"a\":{\"b\":3,\"y\":2},\"items\":[{\"c\":5,\"d\":4}],\"z\":1}",factory.canonical(root));
    }

    private RecruitmentInterview interview(UUID sectionId) throws Exception {
        RecruitmentDtos.Question question=new RecruitmentDtos.Question(UUID.randomUUID(),1,"Describe a system.",
                "Engineering","Look for clear decisions",1,InterviewInferenceApi.QuestionSource.TEMPLATE,null);
        RecruitmentDtos.Section section=new RecruitmentDtos.Section(sectionId,1,InterviewInferenceApi.SectionKind.CORE,
                "en-US",300,"",List.of(question));
        RecruitmentDtos.RevisionContent content=new RecruitmentDtos.RevisionContent("Welcome","Template disclosure","Thanks",300,
                new RecruitmentDtos.InteractionLimits(1,1,10,1),List.of(section));
        RecruitmentInterview value=new RecruitmentInterview();value.setId(UUID.randomUUID());value.setTenantId(UUID.randomUUID());
        value.setApplicationId(UUID.randomUUID());value.setJobId(UUID.randomUUID());value.setTemplateRevisionId(UUID.randomUUID());
        value.setTemplateSnapshot(mapper.writeValueAsString(content));value.setTemplateSnapshotSha256("b".repeat(64));
        value.setTemplateSnapshotVersion("1");return value;
    }
    private RecruitmentJob job(){RecruitmentJob value=new RecruitmentJob();value.setFrozenCompanyName("Acme");value.setLanguage("en-US");return value;}
    private RecruitmentCandidate candidate(){RecruitmentCandidate value=new RecruitmentCandidate();value.setFullName("Candidate");return value;}
    private static String sha256(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));}
}
