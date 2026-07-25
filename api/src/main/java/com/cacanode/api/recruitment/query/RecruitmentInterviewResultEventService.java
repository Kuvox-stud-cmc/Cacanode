package com.cacanode.api.recruitment.query;

import com.cacanode.api.recruitment.api.InterviewEventIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import com.cacanode.api.recruitment.repository.RecruitmentInterviewRepository;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import com.cacanode.api.recruitment.service.RecruitmentProjectionEventPublisher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.messaging-enabled:false}")
public class RecruitmentInterviewResultEventService {
    private static final Set<String> TURN_V11=Set.of("schema_version","event_id","event_type","occurred_at",
            "tenant_id","aggregate_id","session_id","call_attempt_id","turn_id","sequence","speaker",
            "turn_kind","section_id","question_id","language_tag","started_at_epoch_ms","ended_at_epoch_ms",
            "transcript","interrupted");
    private static final Set<String> TURN_V10=Set.of("schema_version","event_id","event_type","occurred_at",
            "tenant_id","aggregate_id","session_id","call_attempt_id","turn_id","sequence","speaker",
            "language_tag","started_at_epoch_ms","ended_at_epoch_ms","transcript","interrupted");
    private static final Set<String> COMPLETED_V11=Set.of("schema_version","event_id","event_type","occurred_at",
            "tenant_id","aggregate_id","session_id","call_attempt_id","completion_reason","expected_turn_count",
            "connected_seconds","partial","score_policy_version","overall_score","english_dimensions",
            "english_band","section_results","question_results");
    private static final Set<String> FAILED_V11=Set.of("schema_version","event_id","event_type","occurred_at",
            "tenant_id","aggregate_id","session_id","call_attempt_id","expected_turn_count","connected_seconds",
            "partial","score_policy_version","overall_score","english_dimensions","english_band","section_results",
            "question_results","failure_code","retryable","detail");
    private static final Set<String> COMPLETED_V10=Set.of("schema_version","event_id","event_type","occurred_at",
            "tenant_id","aggregate_id","session_id","call_attempt_id","completion_reason","connected_seconds",
            "turn_count","section_results");
    private static final Set<String> FAILED_V10=Set.of("schema_version","event_id","event_type","occurred_at",
            "tenant_id","aggregate_id","session_id","call_attempt_id","failure_code","retryable",
            "connected_seconds","last_turn_sequence","detail");
    private static final Set<String> USAGE_FIELDS=Set.of("schema_version","event_id","event_type","occurred_at",
            "tenant_id","aggregate_id","usage_id","session_id","call_attempt_id","provider","capability",
            "quantity","unit","provider_request_id");
    private static final Set<String> TURN_KINDS=Set.of("INTRODUCTION","TRANSITION","QUESTION","ACKNOWLEDGEMENT",
            "FOLLOW_UP","CLARIFICATION","REPETITION","SILENCE_PROMPT","CANDIDATE_UTTERANCE","CLOSING");
    private static final Set<String> ACTIVE_INTERVIEWS=Set.of("INVITED","SCHEDULED","PREPARING","CALLING",
            "RINGING","CONSENT_PENDING","IN_PROGRESS");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    @Autowired(required=false) private RecruitmentInterviewRepository interviews;
    @Autowired(required=false) private RecruitmentApplicationRepository applications;
    @Autowired(required=false) private RecruitmentProjectionEventPublisher projectionEvents;

    @Transactional
    public void accept(byte[] raw) {
        JsonNode root=read(raw);String type=text(root,"event_type");
        switch(type){
            case "interview.turn.finalized" -> acceptTurn(root);
            case "interview.session.completed" -> acceptTerminal(root,false);
            case "interview.session.failed" -> acceptTerminal(root,true);
            case "interview.provider.usage" -> acceptUsage(root);
            default -> reject("Unsupported recruitment interview event");
        }
    }

    private void acceptTurn(JsonNode root) {
        boolean v11="1.1".equals(text(root,"schema_version"));
        exact(root,v11?TURN_V11:TURN_V10,"turn");Common common=common(root);Binding binding=binding(common);
        int sequence=integer(root,"sequence",v11?1:0,500);UUID turnId=uuid(root,"turn_id");
        Integer terminalExpected=jdbc.query("SELECT expected_turn_count FROM recruitment_interview_results WHERE tenant_id=? AND session_id=?",
                rs->rs.next()?rs.getInt(1):null,common.tenantId,common.sessionId);
        if(terminalExpected!=null)expect(sequence<=terminalExpected,"Turn exceeds terminal expected count");
        if(v11){
            expect(InterviewEventIdentity.eventId(common.type,common.sessionId,"turn:"+sequence+":v1.1").equals(common.eventId),"Invalid turn event identity");
            expect(InterviewEventIdentity.turnId(common.sessionId,sequence).equals(turnId),"Invalid turn identity");
        }
        String speaker=boundedEnum(root,"speaker",Set.of("CANDIDATE","INTERVIEWER","SYSTEM"));
        String kind=v11?boundedEnum(root,"turn_kind",TURN_KINDS):("CANDIDATE".equals(speaker)?"CANDIDATE_UTTERANCE":"QUESTION");
        UUID section=nullableUuid(root,"section_id"),question=nullableUuid(root,"question_id");
        Snapshot snapshot=snapshot(binding.preparedSession);
        if(section!=null)expect(snapshot.sections.containsKey(section),"Unknown transcript section");
        if(question!=null)expect(snapshot.questions.containsKey(question),"Unknown transcript question");
        if(question!=null)expect(Objects.equals(snapshot.questionSections.get(question),section),"Transcript question/section mismatch");
        if(v11&&Set.of("QUESTION","FOLLOW_UP","CLARIFICATION","REPETITION").contains(kind))
            expect(section!=null&&question!=null,"Question-scoped turn is missing context");
        String language=boundedEnum(root,"language_tag",Set.of("vi-VN","en-US"));
        long started=longValue(root,"started_at_epoch_ms",0,Long.MAX_VALUE),ended=longValue(root,"ended_at_epoch_ms",started,Long.MAX_VALUE);
        String transcript=text(root,"transcript");expect(!transcript.isBlank()&&transcript.length()<=8000,"Invalid transcript bounds");
        String hash=canonicalHash(root),semantic=v11?"turn:"+sequence+":v1.1":"turn:"+sequence;
        if(replay(common,hash))return;
        Integer existing=jdbc.query("SELECT sequence_number FROM recruitment_interview_transcript_turns WHERE tenant_id=? AND session_id=? AND sequence_number=?",
                rs->rs.next()?rs.getInt(1):null,common.tenantId,common.sessionId,sequence);
        expect(existing==null,"Conflicting transcript sequence replay");
        inbox(common,semantic,hash,root,"APPLIED");
        jdbc.update("""
                INSERT INTO recruitment_interview_transcript_turns(turn_id,tenant_id,session_id,call_attempt_id,event_id,
                sequence_number,speaker,turn_kind,section_id,question_id,language_tag,started_at_epoch_ms,
                ended_at_epoch_ms,transcript,interrupted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,turnId,common.tenantId,common.sessionId,common.callAttemptId,common.eventId,sequence,speaker,kind,
                section,question,language,started,ended,transcript,bool(root,"interrupted"));
        reconcile(common.tenantId,common.sessionId);
    }

    private void acceptTerminal(JsonNode root,boolean failed) {
        boolean v11="1.1".equals(text(root,"schema_version"));
        exact(root,v11?(failed?FAILED_V11:COMPLETED_V11):(failed?FAILED_V10:COMPLETED_V10),"terminal event");
        Common common=common(root);Binding binding=binding(common);String semantic=(failed?"failed":"completed")+(v11?":v1.1":":v1");
        if(v11)expect(InterviewEventIdentity.eventId(common.type,common.sessionId,semantic).equals(common.eventId),"Invalid terminal event identity");
        String hash=canonicalHash(root);if(replay(common,hash))return;
        Integer terminalCount=jdbc.query("SELECT count(*) FROM recruitment_interview_results WHERE tenant_id=? AND session_id=?",
                rs->{rs.next();return rs.getInt(1);},common.tenantId,common.sessionId);
        expect(terminalCount==0,"Interview already has an immutable terminal result");
        Terminal terminal=v11?validateTerminalV11(root,binding,failed):legacyTerminal(root,binding,failed);
        int persisted=countTurns(common.tenantId,common.sessionId);
        String delivery=persisted>=terminal.expectedTurns?"COMPLETE":"PENDING_TURNS";
        inbox(common,semantic,hash,root,delivery.equals("COMPLETE")?"APPLIED":"PENDING_TURNS");
        jdbc.update("""
                INSERT INTO recruitment_interview_results(session_id,tenant_id,call_attempt_id,terminal_event_id,
                terminal_kind,delivery_status,completion_reason,failure_code,retryable,failure_detail,partial,
                expected_turn_count,persisted_turn_count,connected_seconds,score_policy_version,overall_score,
                english_comprehension,english_fluency,english_vocabulary,english_grammar,english_pronunciation,
                english_band,occurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,common.sessionId,common.tenantId,common.callAttemptId,common.eventId,failed?"FAILED":"COMPLETED",
                delivery,terminal.completionReason,terminal.failureCode,terminal.retryable,terminal.detail,terminal.partial,
                terminal.expectedTurns,persisted,terminal.connectedSeconds,terminal.policy,terminal.overall,
                dimension(terminal.english,"comprehension"),dimension(terminal.english,"fluency"),
                dimension(terminal.english,"vocabulary"),dimension(terminal.english,"grammar"),
                dimension(terminal.english,"pronunciation"),terminal.band,common.occurredAt);
        persistResultChildren(common,root,v11);
        mirrorBusinessState(binding,terminal,failed);
        reconcile(common.tenantId,common.sessionId);
    }

    private void acceptUsage(JsonNode root) {
        exact(root,USAGE_FIELDS,"provider usage");Common common=common(root);binding(common);
        UUID usageId=uuid(root,"usage_id");String provider=boundedEnum(root,"provider",Set.of("TWILIO","CARTESIA","OPENAI","OLLAMA"));
        String capability=boundedEnum(root,"capability",Set.of("VOICE_CALL","MEDIA_STREAM","STT","TTS","LLM"));
        String unit=boundedEnum(root,"unit",Set.of("CONNECTED_SECOND","AUDIO_SECOND","CHARACTER","TOKEN"));
        BigDecimal quantity=decimal(root,"quantity");expect(quantity.signum()>0&&quantity.compareTo(new BigDecimal("1000000000"))<=0,"Invalid provider usage quantity");
        boolean v11="1.1".equals(common.version);if(v11){String semantic=provider.toLowerCase(Locale.ROOT)+":"+capability.toLowerCase(Locale.ROOT)+":v1.1";
            expect(common.eventId.equals(usageId)&&InterviewEventIdentity.eventId(common.type,common.sessionId,semantic).equals(common.eventId),"Invalid usage identity");}
        String semantic=v11?provider.toLowerCase(Locale.ROOT)+":"+capability.toLowerCase(Locale.ROOT)+":v1.1":provider.toLowerCase(Locale.ROOT)+":"+capability.toLowerCase(Locale.ROOT);
        String hash=canonicalHash(root);if(replay(common,hash))return;inbox(common,semantic,hash,root,"APPLIED");
        Integer existing=jdbc.query("SELECT count(*) FROM recruitment_interview_provider_usage WHERE tenant_id=? AND session_id=? AND provider=? AND capability=?",
                rs->{rs.next();return rs.getInt(1);},common.tenantId,common.sessionId,provider,capability);
        expect(existing==0,"Conflicting provider usage replay");
        jdbc.update("""
                INSERT INTO recruitment_interview_provider_usage(usage_id,tenant_id,session_id,call_attempt_id,event_id,
                provider,capability,quantity,unit,provider_request_id,occurred_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,usageId,common.tenantId,common.sessionId,common.callAttemptId,common.eventId,provider,capability,
                quantity,unit,nullableText(root,"provider_request_id"),common.occurredAt);
    }

    private Terminal validateTerminalV11(JsonNode root,Binding binding,boolean failed) {
        expect("equal-core-questions-v1".equals(text(root,"score_policy_version")),"Unsupported score policy");
        int turns=integer(root,"expected_turn_count",0,500),seconds=integer(root,"connected_seconds",0,14400);
        JsonNode sections=root.path("section_results"),questions=root.path("question_results");
        expect(sections.isArray()&&sections.size()<=10&&questions.isArray()&&questions.size()<=100,"Invalid result arrays");
        Snapshot snapshot=snapshot(binding.preparedSession);Set<UUID> seenSections=new HashSet<>(),seenQuestions=new HashSet<>();
        List<BigDecimal> coreScores=new ArrayList<>();List<JsonNode> englishEvaluations=new ArrayList<>();Set<UUID> allEvidence=new HashSet<>();
        for(JsonNode section:sections){exact(section,Set.of("section_id","kind","status"),"section result");UUID id=uuid(section,"section_id");
            expect(seenSections.add(id)&&snapshot.sections.containsKey(id),"Invalid result section");
            expect(snapshot.sections.get(id).equals(text(section,"kind")),"Result section kind mismatch");
            boundedEnum(section,"status",Set.of("COMPLETED","PARTIAL","SKIPPED"));}
        for(JsonNode question:questions){exact(question,Set.of("section_id","question_id","section_kind","status","score","evaluations"),"question result");
            UUID sectionId=uuid(question,"section_id"),questionId=uuid(question,"question_id");
            expect(seenQuestions.add(questionId)&&snapshot.questions.containsKey(questionId),"Invalid result question");
            expect(snapshot.questionSections.get(questionId).equals(sectionId)&&snapshot.sections.get(sectionId).equals(text(question,"section_kind")),"Result question binding mismatch");
            boundedEnum(question,"status",Set.of("COMPLETED","PARTIAL","UNANSWERED","SKIPPED"));JsonNode evaluations=question.path("evaluations");
            expect(evaluations.isArray()&&evaluations.size()<=20,"Invalid score evaluations");List<BigDecimal> accepted=new ArrayList<>();Set<UUID> evidence=new HashSet<>();
            for(JsonNode evaluation:evaluations){exact(evaluation,Set.of("candidate_turn_id","accepted","rubric_score","english_dimensions"),"score evaluation");
                UUID turn=uuid(evaluation,"candidate_turn_id");expect(evidence.add(turn)&&allEvidence.add(turn),"Duplicate evidence turn");boolean acceptedValue=bool(evaluation,"accepted");
                BigDecimal score=nullableDecimal(evaluation,"rubric_score");expect(acceptedValue==(score!=null),"Invalid accepted score evaluation");
                if(score!=null){expect(score.compareTo(BigDecimal.ONE)>=0&&score.compareTo(new BigDecimal("5"))<=0,"Invalid rubric score");accepted.add(score);}
                JsonNode english=evaluation.get("english_dimensions");if(english!=null&&!english.isNull()){expect(acceptedValue&&"ENGLISH_SCREEN".equals(text(question,"section_kind")),"Invalid English evaluation");validateDimensions(english);englishEvaluations.add(english);}}
            BigDecimal expected=accepted.isEmpty()?null:mean(accepted);BigDecimal supplied=nullableDecimal(question,"score");expect(equal(expected,supplied),"Question score arithmetic mismatch");
            if(Set.of("UNANSWERED","SKIPPED").contains(text(question,"status")))expect(expected==null,"Unanswered question contains a score");
            if(expected!=null&&"CORE".equals(text(question,"section_kind")))coreScores.add(expected);
        }
        expect(seenSections.equals(snapshot.sections.keySet()),"Section results do not cover the prepared snapshot");
        expect(seenQuestions.equals(snapshot.questions.keySet()),"Question results do not cover the prepared snapshot");
        expect(allEvidence.size()<=turns,"Evidence count exceeds expected transcript turns");
        BigDecimal expectedOverall=coreScores.isEmpty()?null:mean(coreScores).multiply(new BigDecimal("20")).setScale(2,RoundingMode.HALF_UP);
        BigDecimal suppliedOverall=nullableDecimal(root,"overall_score");expect(equal(expectedOverall,suppliedOverall),"Overall score arithmetic mismatch");
        JsonNode english=root.get("english_dimensions");String band=nullableText(root,"english_band");
        if(englishEvaluations.isEmpty())expect((english==null||english.isNull())&&band==null,"Unexpected English result");
        else{expect(english!=null&&!english.isNull()&&band!=null,"Incomplete English result");validateDimensions(english);Map<String,BigDecimal> means=dimensionMeans(englishEvaluations);
            for(String field:means.keySet())expect(equal(means.get(field),decimal(english,field)),"English dimension arithmetic mismatch");
            BigDecimal total=means.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(new BigDecimal("5"),8,RoundingMode.HALF_UP);
            String expectedBand=total.compareTo(new BigDecimal("2"))<0?"BASIC":total.compareTo(new BigDecimal("3"))<0?"CONVERSATIONAL":total.compareTo(new BigDecimal("4"))<0?"WORKING_PROFICIENCY":"PROFESSIONAL";
            expect(expectedBand.equals(band),"English band mismatch");}
        String completion=failed?null:boundedEnum(root,"completion_reason",Set.of("FINISHED","CANDIDATE_STOPPED","TIME_LIMIT","PARTIAL"));
        if(!failed)expect(("FINISHED".equals(completion))!=bool(root,"partial"),"Completion reason/partial mismatch");
        String code=failed?text(root,"failure_code"):null;Boolean retryable=failed?bool(root,"retryable"):null;String detail=failed?text(root,"detail"):null;
        if(failed)expect(!code.isBlank()&&code.length()<=100&&detail.length()<=1000,"Invalid failed result bounds");
        return new Terminal(turns,seconds,bool(root,"partial"),text(root,"score_policy_version"),expectedOverall,english,band,completion,code,retryable,detail);
    }

    private Terminal legacyTerminal(JsonNode root,Binding binding,boolean failed) {
        int turns=failed?(root.path("last_turn_sequence").isNull()?0:integer(root,"last_turn_sequence",0,500)):integer(root,"turn_count",0,500);
        int seconds=integer(root,"connected_seconds",0,14400);String completion=failed?null:boundedEnum(root,"completion_reason",Set.of("FINISHED","CANDIDATE_STOPPED","TIME_LIMIT","PARTIAL"));
        return new Terminal(turns,seconds,true,"legacy-v1.0",null,null,null,completion,failed?text(root,"failure_code"):null,
                failed?bool(root,"retryable"):null,failed?text(root,"detail"):null);
    }

    private void persistResultChildren(Common common,JsonNode root,boolean v11) {
        JsonNode sections=root.path("section_results");int sectionPosition=0;
        for(JsonNode section:sections){UUID id=uuid(section,"section_id");jdbc.update("INSERT INTO recruitment_interview_section_results(tenant_id,session_id,section_id,section_kind,section_status,position) VALUES (?,?,?,?,?,?)",
                common.tenantId,common.sessionId,id,text(section,"kind"),text(section,"status"),++sectionPosition);}
        if(!v11)return;int questionPosition=0;
        for(JsonNode question:root.path("question_results")){UUID sectionId=uuid(question,"section_id"),questionId=uuid(question,"question_id");
            jdbc.update("INSERT INTO recruitment_interview_question_results(tenant_id,session_id,section_id,question_id,section_kind,question_status,question_score,position) VALUES (?,?,?,?,?,?,?,?)",
                    common.tenantId,common.sessionId,sectionId,questionId,text(question,"section_kind"),text(question,"status"),nullableDecimal(question,"score"),++questionPosition);
            persistAvailableEvaluations(common,sectionId,questionId,question.path("evaluations"));}
    }

    private void persistAvailableEvaluations(Common common,UUID sectionId,UUID questionId,JsonNode evaluations) {
        int position=0;for(JsonNode evaluation:evaluations){position++;UUID turnId=uuid(evaluation,"candidate_turn_id");
            Map<String,Object> turn=jdbc.query("SELECT speaker,section_id,question_id FROM recruitment_interview_transcript_turns WHERE tenant_id=? AND session_id=? AND turn_id=?",
                    rs->{if(!rs.next())return null;Map<String,Object> value=new HashMap<>();value.put("speaker",rs.getString(1));value.put("section",rs.getObject(2,UUID.class));value.put("question",rs.getObject(3,UUID.class));return value;},common.tenantId,common.sessionId,turnId);
            if(turn==null)continue;expect("CANDIDATE".equals(turn.get("speaker"))&&Objects.equals(turn.get("section"),sectionId)&&Objects.equals(turn.get("question"),questionId),"Evidence turn binding mismatch");
            JsonNode english=evaluation.get("english_dimensions");jdbc.update("""
                    INSERT INTO recruitment_interview_score_evaluations(tenant_id,session_id,section_id,question_id,
                    candidate_turn_id,accepted,rubric_score,english_comprehension,english_fluency,english_vocabulary,
                    english_grammar,english_pronunciation,position) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                    """,common.tenantId,common.sessionId,sectionId,questionId,turnId,bool(evaluation,"accepted"),
                    nullableDecimal(evaluation,"rubric_score"),dimension(english,"comprehension"),dimension(english,"fluency"),
                    dimension(english,"vocabulary"),dimension(english,"grammar"),dimension(english,"pronunciation"),position);}
    }

    private void reconcile(UUID tenantId,UUID sessionId) {
        Map<String,Object> result=jdbc.query("SELECT terminal_event_id,expected_turn_count FROM recruitment_interview_results WHERE tenant_id=? AND session_id=?",
                rs->{if(!rs.next())return null;return Map.of("event",rs.getObject(1,UUID.class),"expected",rs.getInt(2));},tenantId,sessionId);
        if(result==null)return;UUID eventId=(UUID)result.get("event");JsonNode terminal=jdbc.query("SELECT canonical_payload FROM recruitment_interview_event_inbox WHERE event_id=?",
                rs->{try{return rs.next()?mapper.readTree(rs.getString(1)):null;}catch(Exception e){throw new IllegalStateException(e);}},eventId);
        if(terminal!=null&&"1.1".equals(text(terminal,"schema_version"))){Common common=common(terminal);for(JsonNode question:terminal.path("question_results"))
            persistAvailableEvaluations(common,uuid(question,"section_id"),uuid(question,"question_id"),question.path("evaluations"));}
        int count=countTurns(tenantId,sessionId),expected=(Integer)result.get("expected");jdbc.update("UPDATE recruitment_interview_results SET persisted_turn_count=?,delivery_status=?,updated_at=NOW() WHERE tenant_id=? AND session_id=?",
                count,count>=expected?"COMPLETE":"PENDING_TURNS",tenantId,sessionId);
        if(count>=expected)jdbc.update("UPDATE recruitment_interview_event_inbox SET processing_status='APPLIED',processed_at=NOW() WHERE event_id=?",eventId);
    }

    private void mirrorBusinessState(Binding binding,Terminal terminal,boolean failed) {
        jdbc.update("UPDATE recruitment_interviews SET overall_score=?,english_band=?,status=CASE WHEN status IN ('INVITED','SCHEDULED','PREPARING','CALLING','RINGING','CONSENT_PENDING','IN_PROGRESS') THEN ? ELSE status END,completed_at=CASE WHEN status IN ('INVITED','SCHEDULED','PREPARING','CALLING','RINGING','CONSENT_PENDING','IN_PROGRESS') THEN NOW() ELSE completed_at END,active_call_attempt_id=NULL,updated_at=NOW() WHERE tenant_id=? AND id=?",
                terminal.overall,terminal.band,failed?"FAILED":"COMPLETED",binding.tenantId,binding.sessionId);
        jdbc.update("UPDATE recruitment_applications SET overall_score=?,english_band=?,status=CASE WHEN ?=false AND status IN ('INTERVIEW_INVITED','INTERVIEW_SCHEDULED') THEN 'INTERVIEW_COMPLETED' ELSE status END,updated_at=NOW() WHERE tenant_id=? AND id=?",
                terminal.overall,terminal.band,failed,binding.tenantId,binding.applicationId);
        if(projectionEvents!=null&&interviews!=null&&applications!=null){
            interviews.findByIdAndTenantId(binding.sessionId,binding.tenantId).ifPresent(value->
                    projectionEvents.interview(value,failed?"interview.failed":"interview.completed"));
            applications.findByIdAndTenantId(binding.applicationId,binding.tenantId).ifPresent(value->
                    projectionEvents.application(value,null));
        }
    }

    private Common common(JsonNode root) {
        String version=text(root,"schema_version"),type=text(root,"event_type");expect(Set.of("1.0","1.1").contains(version),"Unsupported interview schema");
        UUID event=uuid(root,"event_id"),tenant=uuid(root,"tenant_id"),aggregate=uuid(root,"aggregate_id"),session=uuid(root,"session_id"),attempt=uuid(root,"call_attempt_id");
        expect(aggregate.equals(session),"Interview aggregate/session mismatch");OffsetDateTime occurred;try{occurred=OffsetDateTime.parse(text(root,"occurred_at"));}catch(Exception e){reject("Invalid event timestamp");return null;}
        return new Common(version,event,type,tenant,session,attempt,occurred);
    }
    private Binding binding(Common common) {Binding value=jdbc.query("""
            SELECT i.application_id,i.status,a.status,ca.prepared_session FROM recruitment_interviews i
            JOIN recruitment_applications a ON a.tenant_id=i.tenant_id AND a.id=i.application_id
            JOIN recruitment_interview_call_attempts ca ON ca.tenant_id=i.tenant_id AND ca.interview_id=i.id
            WHERE i.tenant_id=? AND i.id=? AND ca.id=? AND ca.session_id=?
            """,rs->{if(!rs.next())return null;return new Binding(common.tenantId,common.sessionId,rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4));},
            common.tenantId,common.sessionId,common.callAttemptId,common.sessionId);expect(value!=null,"Interview event binding mismatch");return value;}
    private Snapshot snapshot(String raw) {try{JsonNode root=mapper.readTree(raw);Map<UUID,String> sections=new LinkedHashMap<>();Map<UUID,JsonNode> questions=new LinkedHashMap<>();Map<UUID,UUID> questionSections=new HashMap<>();
        for(JsonNode section:root.path("sections")){UUID sectionId=UUID.fromString(section.path("sectionId").asText());sections.put(sectionId,section.path("kind").asText());for(JsonNode question:section.path("questions")){UUID questionId=UUID.fromString(question.path("questionId").asText());questions.put(questionId,question);questionSections.put(questionId,sectionId);}}
        return new Snapshot(sections,questions,questionSections);}catch(Exception e){throw new IllegalStateException("Stored prepared session is invalid",e);}}
    private boolean replay(Common common,String hash) {Map<String,String> exact=jdbc.query("SELECT payload_sha256,event_type FROM recruitment_interview_event_inbox WHERE event_id=?",
            rs->{if(!rs.next())return null;return Map.of("hash",rs.getString(1),"type",rs.getString(2));},common.eventId);if(exact==null)return false;
        expect(exact.get("hash").equals(hash)&&exact.get("type").equals(common.type),"Conflicting interview event replay");return true;}
    private void inbox(Common common,String semantic,String hash,JsonNode root,String status) {jdbc.update("""
            INSERT INTO recruitment_interview_event_inbox(event_id,tenant_id,session_id,call_attempt_id,schema_version,
            event_type,semantic_key,payload_sha256,canonical_payload,processing_status,occurred_at)
            VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?)
            """,common.eventId,common.tenantId,common.sessionId,common.callAttemptId,common.version,common.type,semantic,hash,canonical(root),status,common.occurredAt);}
    private int countTurns(UUID tenant,UUID session){Integer value=jdbc.query("SELECT count(*) FROM recruitment_interview_transcript_turns WHERE tenant_id=? AND session_id=?",rs->{rs.next();return rs.getInt(1);},tenant,session);return value==null?0:value;}
    private JsonNode read(byte[] raw){try{JsonNode value=mapper.readTree(raw);expect(value!=null&&value.isObject(),"Malformed interview event");return value;}catch(Exception e){reject("Malformed interview event JSON");return null;}}
    private String canonicalHash(JsonNode root){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(root).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String canonical(JsonNode root){try{return mapper.writeValueAsString(canonicalValue(root));}catch(Exception e){throw new IllegalStateException(e);}}
    private Object canonicalValue(JsonNode node){if(node.isObject()){Map<String,Object> result=new TreeMap<>();node.fields().forEachRemaining(e->result.put(e.getKey(),canonicalValue(e.getValue())));return result;}if(node.isArray()){List<Object> result=new ArrayList<>();node.forEach(v->result.add(canonicalValue(v)));return result;}return mapper.convertValue(node,Object.class);}
    private static void exact(JsonNode node,Set<String> fields,String label){expect(node!=null&&node.isObject(),"Invalid "+label);Set<String> actual=new HashSet<>();node.fieldNames().forEachRemaining(actual::add);expect(actual.equals(fields),"Unknown or missing "+label+" fields");}
    private static String text(JsonNode node,String field){JsonNode value=node.get(field);expect(value!=null&&value.isTextual(),"Invalid "+field);return value.asText();}
    private static String nullableText(JsonNode node,String field){JsonNode value=node.get(field);if(value==null||value.isNull())return null;expect(value.isTextual()&&!value.asText().isBlank(),"Invalid "+field);return value.asText();}
    private static UUID uuid(JsonNode node,String field){try{return UUID.fromString(text(node,field));}catch(Exception e){reject("Invalid "+field);return null;}}
    private static UUID nullableUuid(JsonNode node,String field){JsonNode value=node.get(field);return value==null||value.isNull()?null:uuid(node,field);}
    private static boolean bool(JsonNode node,String field){JsonNode value=node.get(field);expect(value!=null&&value.isBoolean(),"Invalid "+field);return value.asBoolean();}
    private static int integer(JsonNode node,String field,int min,int max){JsonNode value=node.get(field);expect(value!=null&&value.isIntegralNumber(),"Invalid "+field);int result=value.asInt();expect(result>=min&&result<=max,"Invalid "+field);return result;}
    private static long longValue(JsonNode node,String field,long min,long max){JsonNode value=node.get(field);expect(value!=null&&value.isIntegralNumber(),"Invalid "+field);long result=value.asLong();expect(result>=min&&result<=max,"Invalid "+field);return result;}
    private static BigDecimal decimal(JsonNode node,String field){JsonNode value=node.get(field);expect(value!=null&&value.isNumber(),"Invalid "+field);return value.decimalValue();}
    private static BigDecimal nullableDecimal(JsonNode node,String field){JsonNode value=node.get(field);return value==null||value.isNull()?null:decimal(node,field);}
    private static String boundedEnum(JsonNode node,String field,Set<String> allowed){String value=text(node,field);expect(allowed.contains(value),"Invalid "+field);return value;}
    private static BigDecimal mean(List<BigDecimal> values){return values.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(values.size()),8,RoundingMode.HALF_UP).stripTrailingZeros();}
    private static boolean equal(BigDecimal left,BigDecimal right){return left==null?right==null:right!=null&&left.compareTo(right)==0;}
    private static void validateDimensions(JsonNode node){exact(node,Set.of("comprehension","fluency","vocabulary","grammar","pronunciation"),"English dimensions");for(String field:List.of("comprehension","fluency","vocabulary","grammar","pronunciation")){BigDecimal value=decimal(node,field);expect(value.compareTo(BigDecimal.ONE)>=0&&value.compareTo(new BigDecimal("5"))<=0,"Invalid English dimension");}}
    private static Map<String,BigDecimal> dimensionMeans(List<JsonNode> values){Map<String,BigDecimal> result=new LinkedHashMap<>();for(String field:List.of("comprehension","fluency","vocabulary","grammar","pronunciation")){List<BigDecimal> scores=new ArrayList<>();values.forEach(v->scores.add(decimal(v,field)));result.put(field,mean(scores));}return result;}
    private static BigDecimal dimension(JsonNode value,String field){return value==null||value.isNull()?null:decimal(value,field);}
    private static void expect(boolean valid,String message){if(!valid)reject(message);}
    private static void reject(String message){throw new AmqpRejectAndDontRequeueException(message);}

    private record Common(String version,UUID eventId,String type,UUID tenantId,UUID sessionId,UUID callAttemptId,OffsetDateTime occurredAt){}
    private record Binding(UUID tenantId,UUID sessionId,UUID applicationId,String interviewStatus,String applicationStatus,String preparedSession){}
    private record Snapshot(Map<UUID,String> sections,Map<UUID,JsonNode> questions,Map<UUID,UUID> questionSections){}
    private record Terminal(int expectedTurns,int connectedSeconds,boolean partial,String policy,BigDecimal overall,JsonNode english,String band,String completionReason,String failureCode,Boolean retryable,String detail){}
}
