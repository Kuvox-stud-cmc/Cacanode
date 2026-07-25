package com.cacanode.api.recruitment.service;

import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

@Component
public class InterviewSessionSnapshotFactory {
    public static final String SNAPSHOT_VERSION="interview-session-v1";
    private final ObjectMapper mapper;

    public InterviewSessionSnapshotFactory(ObjectMapper source) {
        mapper=source.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
    }

    public Snapshot build(RecruitmentInterview interview,RecruitmentJob job,
            RecruitmentCandidate candidate,RecruitmentCvAnalysis analysis,UUID callAttemptId) {
        try {
            RecruitmentDtos.RevisionContent template=mapper.readValue(
                    interview.getTemplateSnapshot(),RecruitmentDtos.RevisionContent.class);
            Map<UUID,List<PersonalizedQuestion>> personalized=personalizedQuestions(template,analysis);
            boolean cvPersonalized=personalized.values().stream().anyMatch(items->!items.isEmpty());
            ObjectNode root=mapper.createObjectNode();
            root.put("snapshotVersion",SNAPSHOT_VERSION);
            root.put("sessionId",interview.getId().toString());
            root.put("callAttemptId",callAttemptId.toString());
            root.put("tenantId",interview.getTenantId().toString());
            root.put("templateRevisionId",interview.getTemplateRevisionId().toString());
            root.put("companyDisplayName",safe(job.getFrozenCompanyName(),"Company"));
            root.put("candidateDisplayName",candidate.getFullName());
            root.put("introductionText",template.introductionText());
            root.put("disclosureText",disclosure(interview,job,cvPersonalized));
            root.put("closingText",template.closingText());
            root.put("durationLimitSeconds",template.durationLimitSeconds());
            root.set("interactionLimits",mapper.valueToTree(template.interactionLimits()));
            root.put("recordingEnabled",interview.isRecordingEnabled());
            root.put("cvPersonalizationEnabled",cvPersonalized);
            ArrayNode sections=root.putArray("sections");
            template.sections().stream().sorted(Comparator.comparingInt(RecruitmentDtos.Section::position))
                    .forEach(section->sections.add(sectionNode(section,personalized.getOrDefault(
                            section.sectionId(),List.of()))));
            normalize(root);
            String hash=sha256(canonical(root));
            root.put("snapshotSha256",hash);
            return new Snapshot(canonical(root),hash,cvPersonalized);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored interview snapshot is invalid",exception);
        }
    }

    public String canonical(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ObjectNode sectionNode(RecruitmentDtos.Section section,List<PersonalizedQuestion> extra) {
        ObjectNode value=mapper.createObjectNode();
        value.put("sectionId",section.sectionId().toString());value.put("position",section.position());
        value.put("kind",section.kind().name());value.put("languageTag",section.languageTag());
        value.put("durationLimitSeconds",section.durationLimitSeconds());
        value.put("transitionText",safe(section.transitionText(),""));
        ArrayNode questions=value.putArray("questions");
        section.questions().stream().sorted(Comparator.comparingInt(RecruitmentDtos.Question::position))
                .forEach(question->questions.add(questionNode(question)));
        int position=section.questions().size()+1;
        for(PersonalizedQuestion question:extra)questions.add(personalizedNode(question,position++));
        return value;
    }

    private ObjectNode questionNode(RecruitmentDtos.Question question) {
        ObjectNode value=mapper.createObjectNode();
        value.put("questionId",question.questionId().toString());value.put("position",question.position());
        value.put("prompt",question.prompt());value.put("competency",question.competency());
        value.put("rubric",question.rubric());value.put("followUpLimit",question.followUpLimit());
        value.put("source",question.source().name());
        if(question.evidence()!=null&&!question.evidence().isBlank())value.put("evidence",question.evidence());
        return value;
    }

    private ObjectNode personalizedNode(PersonalizedQuestion question,int position) {
        ObjectNode value=mapper.createObjectNode();
        value.put("questionId",question.questionId().toString());value.put("position",position);
        value.put("prompt",question.prompt());value.put("competency",question.competency());
        value.put("rubric",question.rubric());value.put("followUpLimit",0);
        value.put("source",InterviewInferenceApi.QuestionSource.CV_PERSONALIZED.name());
        value.put("evidence",String.join(" ",question.excerpts()));
        return value;
    }

    private Map<UUID,List<PersonalizedQuestion>> personalizedQuestions(
            RecruitmentDtos.RevisionContent template,RecruitmentCvAnalysis analysis) throws JsonProcessingException {
        if(analysis==null||analysis.getStatus()!=RecruitmentEnums.CvAnalysisRecordStatus.COMPLETED
                ||analysis.getAnalysisMode()!=RecruitmentEnums.CvAiMode.PERSONALIZED_QUESTIONS)return Map.of();
        Set<UUID> coreIds=new HashSet<>();
        template.sections().stream().filter(s->s.kind()==InterviewInferenceApi.SectionKind.CORE)
                .forEach(s->coreIds.add(s.sectionId()));
        Map<String,String> evidence=new HashMap<>();
        for(JsonNode item:mapper.readTree(analysis.getEvidence()))
            evidence.put(item.path("anchor_id").asText(),item.path("excerpt").asText());
        Map<UUID,List<PersonalizedQuestion>> result=new HashMap<>();
        for(JsonNode item:mapper.readTree(analysis.getPersonalizedQuestions())) {
            UUID sectionId=parseUuid(item.path("target_section_id").asText());
            UUID questionId=parseUuid(item.path("question_id").asText());
            if(sectionId==null||questionId==null||!coreIds.contains(sectionId))continue;
            List<String> excerpts=new ArrayList<>();
            for(JsonNode anchor:item.path("evidence_anchor_ids")) {
                String excerpt=evidence.get(anchor.asText());
                if(excerpt!=null&&!excerpt.isBlank())excerpts.add(excerpt);
            }
            if(excerpts.isEmpty())continue;
            result.computeIfAbsent(sectionId,ignored->new ArrayList<>()).add(new PersonalizedQuestion(
                    questionId,item.path("prompt").asText(),item.path("competency").asText(),
                    item.path("rubric").asText(),List.copyOf(excerpts)));
        }
        return result;
    }

    private static String disclosure(RecruitmentInterview interview,RecruitmentJob job,boolean cv) {
        String company=safe(job.getFrozenCompanyName(),"the hiring company");
        String locale="vi-VN".equals(job.getLanguage())?"vi":"en";
        String recordingVi=interview.isRecordingEnabled()?" Cuộc gọi sẽ được ghi âm hai kênh sau khi bạn đồng ý và được lưu trong "+interview.getRecordingRetentionDays()+" ngày.":" Cuộc gọi này không được ghi âm.";
        String recordingEn=interview.isRecordingEnabled()?" The call will be recorded in dual channel after you consent and retained for "+interview.getRecordingRetentionDays()+" days.":" This call is not recorded.";
        if("vi".equals(locale))return "Đây là cuộc phỏng vấn có hỗ trợ bởi AI của "+company+
                " cho mục đích tuyển dụng. Hồ sơ CV của bạn "+(cv?"đã":"không")+
                " được dùng để cá nhân hóa câu hỏi."+recordingVi+" Nhấn phím 1 để đồng ý hoặc phím 2 để từ chối.";
        return "This is an AI-assisted interview for "+company+
                " for hiring purposes. Your CV was "+(cv?"used":"not used")+
                " to personalize questions."+recordingEn+" Press 1 to consent or 2 to decline.";
    }

    private static void normalize(JsonNode node) {
        if(node.isObject()) {
            ObjectNode object=(ObjectNode)node;
            List<String> names=new ArrayList<>();object.fieldNames().forEachRemaining(names::add);
            for(String name:names) {
                JsonNode child=object.get(name);
                if(child.isTextual())object.put(name,Normalizer.normalize(child.textValue(),Normalizer.Form.NFC));
                else normalize(child);
            }
        } else if(node.isArray())node.forEach(InterviewSessionSnapshotFactory::normalize);
    }

    private static UUID parseUuid(String value){try{return UUID.fromString(value);}catch(RuntimeException ignored){return null;}}
    private static String safe(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception exception){throw new IllegalStateException(exception);}}

    public record Snapshot(String json,String sha256,boolean cvPersonalizationEnabled) {}
    private record PersonalizedQuestion(UUID questionId,String prompt,String competency,String rubric,List<String> excerpts) {}
}
