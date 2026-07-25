package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.api.InterviewEventIdentity;
import com.cacanode.api.recruitment.api.event.ResumeAnalysisOutcomeEvent;
import com.cacanode.api.recruitment.config.CvAnalysisProperties;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.messaging-enabled:false} and ${app.recruitment.cv-ai-enabled:false}")
public class RecruitmentCvAnalysisOutcomeService {
    private static final Set<String> ROOT_FIELDS=Set.of("schema_version","event_id","event_type","occurred_at",
            "tenant_id","aggregate_id","analysis_id","application_id","cv_sha256","analysis_mode",
            "policy_version","model_version","status","summary","evidence","skills","personalized_questions","error_code");
    private final RecruitmentCvAnalysisRepository analyses;private final RecruitmentCvAnalysisInboxRepository inbox;
    private final RecruitmentApplicationRepository applications;private final ObjectMapper mapper;
    private final CvAnalysisProperties properties;private final Clock clock;

    @Transactional
    public void accept(byte[] raw){
        JsonNode tree=readTree(raw);requireFields(tree,ROOT_FIELDS,"outcome");validateShape(tree);
        ResumeAnalysisOutcomeEvent event=read(raw);String digest=canonicalHash(tree);
        validateIdentity(event);
        RecruitmentCvAnalysisInbox exact=inbox.findById(event.eventId()).orElse(null);
        if(exact!=null){if(exact.getPayloadSha256().equals(digest))return;reject("Conflicting outcome event replay");}
        RecruitmentCvAnalysisInbox semantic=inbox.findByTenantIdAndAnalysisId(event.tenantId(),event.analysisId()).orElse(null);
        if(semantic!=null)reject("A different semantic outcome was already accepted");
        RecruitmentCvAnalysis analysis=analyses.findForUpdate(event.tenantId(),event.analysisId()).orElse(null);
        if(analysis==null){record(event,digest,"IGNORED_CANCELLED");return;}
        validateBinding(event,analysis);
        if(analysis.getStatus()==CvAnalysisRecordStatus.CANCELLED){record(event,digest,"IGNORED_CANCELLED");return;}
        if(analysis.getStatus()==CvAnalysisRecordStatus.COMPLETED||analysis.getStatus()==CvAnalysisRecordStatus.FAILED)
            reject("Analysis already has a terminal outcome");
        validateOutput(event,analysis);
        analysis.setOutcomeEventId(event.eventId());analysis.setOutcomePayloadSha256(digest);analysis.setCompletedAt(LocalDateTime.now(clock));
        if("COMPLETED".equals(event.status())){
            analysis.setStatus(CvAnalysisRecordStatus.COMPLETED);analysis.setSummary(event.summary().strip());
            analysis.setEvidence(write(event.evidence()));analysis.setSkills(write(event.skills()));
            analysis.setPersonalizedQuestions(write(event.personalizedQuestions()));analysis.setFailureCode(null);
        }else{
            analysis.setStatus(CvAnalysisRecordStatus.FAILED);analysis.setFailureCode(event.errorCode().strip());
            analysis.setSummary(null);analysis.setEvidence("[]");analysis.setSkills("[]");analysis.setPersonalizedQuestions("[]");
        }
        analyses.save(analysis);
        applications.findForUpdate(event.tenantId(),event.applicationId()).ifPresent(application->{
            if(event.analysisId().equals(application.getActiveCvAnalysisId())){
                application.setCvAnalysisStatus("COMPLETED".equals(event.status())?CvAnalysisStatus.COMPLETED:CvAnalysisStatus.FAILED);
                applications.save(application);
            }
        });
        record(event,digest,"COMPLETED".equals(event.status())?"APPLIED_COMPLETED":"APPLIED_FAILED");
    }

    private void validateIdentity(ResumeAnalysisOutcomeEvent event){
        if(!"1.1".equals(event.schemaVersion())||!"interview.resume-analysis.outcome".equals(event.eventType())
                ||event.analysisId()==null||!event.analysisId().equals(event.aggregateId())
                ||!InterviewEventIdentity.eventId(event.eventType(),event.analysisId(),"outcome:v1.1").equals(event.eventId()))
            reject("Invalid outcome identity");
    }
    private void validateBinding(ResumeAnalysisOutcomeEvent event,RecruitmentCvAnalysis analysis){
        if(!analysis.getApplicationId().equals(event.applicationId())||!analysis.getCvSha256().equals(event.cvSha256())
                ||!analysis.getAnalysisMode().name().equals(event.analysisMode())
                ||!analysis.getPolicyVersion().equals(event.policyVersion())||!analysis.getModelVersion().equals(event.modelVersion()))
            reject("Outcome binding does not match the requested analysis");
    }
    private void validateOutput(ResumeAnalysisOutcomeEvent event,RecruitmentCvAnalysis analysis){
        if(!Set.of("COMPLETED","FAILED").contains(event.status()))reject("Invalid outcome status");
        List<ResumeAnalysisOutcomeEvent.Evidence> evidence=nonnull(event.evidence());
        List<ResumeAnalysisOutcomeEvent.Skill> skills=nonnull(event.skills());
        List<ResumeAnalysisOutcomeEvent.PersonalizedQuestion> questions=nonnull(event.personalizedQuestions());
        if("FAILED".equals(event.status())){
            if(blank(event.errorCode())||event.errorCode().length()>100||!blank(event.summary())
                    ||!evidence.isEmpty()||!skills.isEmpty()||!questions.isEmpty())reject("Invalid failed outcome");return;
        }
        if(blank(event.summary())||event.summary().length()>4000||event.errorCode()!=null
                ||evidence.size()>properties.maxEvidenceSegments()||questions.size()>properties.maxPersonalizedQuestions())
            reject("Invalid completed outcome bounds");
        if(analysis.getAnalysisMode()==CvAiMode.SUMMARY_ONLY&&!questions.isEmpty())reject("Summary-only outcome contains questions");
        Set<String> anchors=new HashSet<>();for(var item:evidence){
            if(blank(item.anchorId())||blank(item.excerpt())||blank(item.sourceLocation())||item.anchorId().length()>80
                    ||item.excerpt().length()>500||item.sourceLocation().length()>120||!anchors.add(item.anchorId()))reject("Invalid evidence anchor");
        }
        for(var skill:skills)if(blank(skill.name())||skill.name().length()>120||nonnull(skill.evidenceAnchorIds()).isEmpty()
                ||!anchors.containsAll(skill.evidenceAnchorIds()))reject("Invalid skill evidence");
        Set<UUID> questionIds=new HashSet<>();Set<String> prompts=new HashSet<>();Set<UUID> allowed=allowedSections(analysis);
        for(var q:questions){String normalized=blank(q.prompt())?"":q.prompt().strip().toLowerCase(Locale.ROOT);
            if(q.questionId()==null||q.targetSectionId()==null||!allowed.contains(q.targetSectionId())
                    ||blank(q.prompt())||q.prompt().length()>1000||blank(q.competency())||q.competency().length()>200
                    ||blank(q.rubric())||q.rubric().length()>2000||nonnull(q.evidenceAnchorIds()).isEmpty()
                    ||!anchors.containsAll(q.evidenceAnchorIds())||!questionIds.add(q.questionId())||!prompts.add(normalized))
                reject("Invalid personalized question");
        }
    }
    private Set<UUID> allowedSections(RecruitmentCvAnalysis analysis){try{
        JsonNode root=mapper.readTree(analysis.getRequestPayload());Set<UUID> values=new HashSet<>();
        for(JsonNode node:root.path("allowed_core_section_ids"))values.add(UUID.fromString(node.asText()));return values;
    }catch(Exception e){throw new IllegalStateException("Stored request payload is invalid",e);}}
    private void record(ResumeAnalysisOutcomeEvent event,String digest,String result){RecruitmentCvAnalysisInbox row=new RecruitmentCvAnalysisInbox();
        row.setEventId(event.eventId());row.setTenantId(event.tenantId());row.setAnalysisId(event.analysisId());
        row.setApplicationId(event.applicationId());row.setPayloadSha256(digest);row.setProcessingResult(result);inbox.save(row);}
    private JsonNode readTree(byte[] raw){try{return mapper.readTree(raw);}catch(Exception e){reject("Malformed outcome JSON");return null;}}
    private ResumeAnalysisOutcomeEvent read(byte[] raw){try{return mapper.readValue(raw,ResumeAnalysisOutcomeEvent.class);}catch(Exception e){reject("Malformed outcome payload");return null;}}
    private void requireFields(JsonNode value,Set<String> expected,String label){if(value==null||!value.isObject())reject("Invalid "+label);
        Set<String> actual=new HashSet<>();value.fieldNames().forEachRemaining(actual::add);if(!actual.equals(expected))reject("Unknown or missing "+label+" fields");}
    private void validateShape(JsonNode root){
        if(!root.path("evidence").isArray()||!root.path("skills").isArray()||!root.path("personalized_questions").isArray())reject("Outcome arrays are invalid");
        root.path("evidence").forEach(v->requireFields(v,Set.of("anchor_id","excerpt","source_location"),"evidence"));
        root.path("skills").forEach(v->requireFields(v,Set.of("name","evidence_anchor_ids"),"skill"));
        root.path("personalized_questions").forEach(v->requireFields(v,Set.of("question_id","target_section_id","prompt","competency","rubric","evidence_anchor_ids"),"question"));
    }
    private String canonicalHash(JsonNode tree){try{return RecruitmentCvAnalysisService.sha256(
            mapper.writeValueAsString(canonical(tree)).getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    private Object canonical(JsonNode node){if(node.isObject()){Map<String,Object> result=new TreeMap<>();node.fields().forEachRemaining(e->result.put(e.getKey(),canonical(e.getValue())));return result;}
        if(node.isArray()){List<Object> values=new ArrayList<>();node.forEach(v->values.add(canonical(v)));return values;}return mapper.convertValue(node,Object.class);}
    private String write(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static <T> List<T> nonnull(List<T> value){return value==null?List.of():value;}
    private static boolean blank(String value){return value==null||value.isBlank();}
    private static void reject(String message){throw new AmqpRejectAndDontRequeueException(message);}
}
