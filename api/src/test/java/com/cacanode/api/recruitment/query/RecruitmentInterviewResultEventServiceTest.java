package com.cacanode.api.recruitment.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecruitmentInterviewResultEventServiceTest {
    private static final Path CONTRACTS=Path.of("../contracts/ai-interview/v1");

    @Test
    void acceptsAttemptScopedV12ProviderUsage() throws Exception {
        StubJdbc jdbc=new StubJdbc(false);
        RecruitmentInterviewResultEventService service=
                new RecruitmentInterviewResultEventService(jdbc,new ObjectMapper());

        service.accept(Files.readAllBytes(CONTRACTS.resolve("provider-usage-v1.2.fixture.json")));

        assertEquals(2,jdbc.updates.size());
        assertTrue(jdbc.updates.get(0).contains("recruitment_interview_event_inbox"));
        assertTrue(jdbc.updates.get(1).contains("recruitment_interview_provider_usage"));
    }

    @Test
    void retainsButDoesNotMaterializeDelayedV11DeliveryFromOlderAttempt() throws Exception {
        StubJdbc jdbc=new StubJdbc(true);
        RecruitmentInterviewResultEventService service=
                new RecruitmentInterviewResultEventService(jdbc,new ObjectMapper());

        service.accept(Files.readAllBytes(CONTRACTS.resolve("provider-usage-v1.1.fixture.json")));

        assertEquals(1,jdbc.updates.size());
        assertTrue(jdbc.updates.getFirst().contains("recruitment_interview_event_inbox"));
    }

    @ParameterizedTest
    @ValueSource(strings={"finalized-turn-v1.2.fixture.json","interview-completed-v1.2.fixture.json",
            "interview-failed-v1.2.fixture.json","provider-usage-v1.2.fixture.json"})
    void retainsButDoesNotMaterializeDelayedV12DeliveryFromOlderAttempt(String fixture) throws Exception {
        StubJdbc jdbc=new StubJdbc(true);
        RecruitmentInterviewResultEventService service=
                new RecruitmentInterviewResultEventService(jdbc,new ObjectMapper());

        service.accept(Files.readAllBytes(CONTRACTS.resolve(fixture)));

        assertEquals(1,jdbc.updates.size());
        assertTrue(jdbc.updates.getFirst().contains("recruitment_interview_event_inbox"));
    }

    @Test
    void recoversVietnameseResultAfterSyntheticMissingResultFailure() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        ObjectNode event=(ObjectNode)mapper.readTree(
                Files.readAllBytes(CONTRACTS.resolve("interview-completed-v1.2.fixture.json")));
        ObjectNode skipped=mapper.createObjectNode();
        skipped.put("section_id","88888888-8888-4888-8888-888888888888");
        skipped.put("question_id","bbbbbbbb-1111-4111-8111-111111111111");
        skipped.put("section_kind","ENGLISH_SCREEN");
        skipped.put("status","SKIPPED");
        skipped.putNull("score");
        skipped.set("evaluations",mapper.createArrayNode());
        ((ArrayNode)event.path("question_results")).insert(1,skipped);
        TerminalStubJdbc jdbc=new TerminalStubJdbc(mapper.writeValueAsString(event));
        RecruitmentInterviewResultEventService service=
                new RecruitmentInterviewResultEventService(jdbc,mapper);

        service.accept(mapper.writeValueAsBytes(event));

        assertTrue(jdbc.containsUpdate("INSERT INTO recruitment_interview_results"));
        assertEquals(3,jdbc.countUpdates("INSERT INTO recruitment_interview_question_results"));
        assertEquals("WORKING_PROFICIENCY",
                jdbc.argsFor("UPDATE recruitment_interviews")[1]);
        assertEquals("WORKING_PROFICIENCY",
                jdbc.argsFor("UPDATE recruitment_applications SET overall_score")[1]);
        assertTrue(jdbc.containsUpdate("UPDATE recruitment_interview_call_attempts"));
        assertEquals(Boolean.TRUE,
                jdbc.argsFor("UPDATE recruitment_interviews")[2]);
        assertEquals("COMPLETED",
                jdbc.argsFor("UPDATE recruitment_interviews")[3]);
    }

    private static final class StubJdbc extends JdbcTemplate {
        private final boolean newerAttemptExists;
        private final List<String> updates=new ArrayList<>();

        private StubJdbc(boolean newerAttemptExists) {
            this.newerAttemptExists=newerAttemptExists;
        }

        @Override
        public <T> T query(String sql,ResultSetExtractor<T> extractor,Object... args) {
            try {
                ResultSet result=mock(ResultSet.class);
                if(sql.contains("JOIN recruitment_applications")) {
                    when(result.next()).thenReturn(true);
                    when(result.getObject(1,UUID.class)).thenReturn(UUID.randomUUID());
                    when(result.getString(2)).thenReturn("IN_PROGRESS");
                    when(result.getString(3)).thenReturn("INTERVIEW_SCHEDULED");
                    when(result.getString(4)).thenReturn("{}");
                } else if(sql.contains("SELECT payload_sha256,event_type")) {
                    when(result.next()).thenReturn(false);
                } else if(sql.contains("SELECT EXISTS")) {
                    when(result.next()).thenReturn(true);
                    when(result.getBoolean(1)).thenReturn(newerAttemptExists);
                } else if(sql.contains("recruitment_interview_provider_usage")) {
                    when(result.next()).thenReturn(true);
                    when(result.getInt(1)).thenReturn(0);
                } else {
                    throw new AssertionError("Unexpected query: "+sql);
                }
                return extractor.extractData(result);
            } catch(SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public int update(String sql,Object... args) {
            updates.add(sql);return 1;
        }
    }

    private static final class TerminalStubJdbc extends JdbcTemplate {
        private static final UUID EVENT=UUID.fromString("ed207cf8-6acc-5636-95e7-05e06ac3e391");
        private static final UUID CORE_SECTION=UUID.fromString("77777777-7777-4777-8777-777777777777");
        private static final UUID ENGLISH_SECTION=UUID.fromString("88888888-8888-4888-8888-888888888888");
        private static final UUID CORE_QUESTION=UUID.fromString("99999999-9999-4999-8999-999999999999");
        private static final UUID ENGLISH_QUESTION=UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111");
        private static final UUID CORE_TURN=UUID.fromString("c307452e-96d1-5745-bbf5-f926e9edffe7");
        private static final UUID ENGLISH_TURN=UUID.fromString("ca7a9b3d-fb0d-5e9c-9ed9-ab6c7a3c7735");
        private static final String PREPARED_SESSION="""
                {"sections":[
                  {"sectionId":"77777777-7777-4777-8777-777777777777","kind":"CORE","languageTag":"vi-VN",
                   "questions":[{"questionId":"99999999-9999-4999-8999-999999999999"}]},
                  {"sectionId":"88888888-8888-4888-8888-888888888888","kind":"ENGLISH_SCREEN","languageTag":"en-US",
                   "questions":[{"questionId":"bbbbbbbb-1111-4111-8111-111111111111"},
                                {"questionId":"aaaaaaaa-1111-4111-8111-111111111111"}]}
                ]}
                """;

        private final String terminalPayload;
        private final List<Update> updates=new ArrayList<>();

        private TerminalStubJdbc(String terminalPayload) {
            this.terminalPayload=terminalPayload;
        }

        @Override
        public <T> T query(String sql,ResultSetExtractor<T> extractor,Object... args) {
            try {
                ResultSet result=mock(ResultSet.class);
                if(sql.contains("JOIN recruitment_applications")) {
                    when(result.next()).thenReturn(true);
                    when(result.getObject(1,UUID.class)).thenReturn(UUID.randomUUID());
                    when(result.getString(2)).thenReturn("FAILED");
                    when(result.getString(3)).thenReturn("INTERVIEW_SCHEDULED");
                    when(result.getString(4)).thenReturn(PREPARED_SESSION);
                    when(result.getString(5)).thenReturn("FAILED");
                    when(result.getString(6)).thenReturn("INTERVIEW_RESULT_MISSING");
                } else if(sql.contains("SELECT payload_sha256,event_type")) {
                    when(result.next()).thenReturn(false);
                } else if(sql.contains("SELECT EXISTS")) {
                    when(result.next()).thenReturn(true);
                    when(result.getBoolean(1)).thenReturn(false);
                } else if(sql.contains("SELECT count(*) FROM recruitment_interview_results")) {
                    when(result.next()).thenReturn(true);
                    when(result.getInt(1)).thenReturn(0);
                } else if(sql.contains("SELECT count(*) FROM recruitment_interview_transcript_turns")) {
                    when(result.next()).thenReturn(true);
                    when(result.getInt(1)).thenReturn(18);
                } else if(sql.contains("SELECT speaker,section_id,question_id")) {
                    Evidence evidence=evidence((UUID)args[2]);
                    when(result.next()).thenReturn(evidence!=null);
                    if(evidence!=null) {
                        when(result.getString(1)).thenReturn("CANDIDATE");
                        when(result.getObject(2,UUID.class)).thenReturn(evidence.sectionId());
                        when(result.getObject(3,UUID.class)).thenReturn(evidence.questionId());
                    }
                } else if(sql.contains("SELECT terminal_event_id,expected_turn_count")) {
                    when(result.next()).thenReturn(true);
                    when(result.getObject(1,UUID.class)).thenReturn(EVENT);
                    when(result.getInt(2)).thenReturn(18);
                } else if(sql.contains("SELECT canonical_payload")) {
                    when(result.next()).thenReturn(true);
                    when(result.getString(1)).thenReturn(terminalPayload);
                } else {
                    throw new AssertionError("Unexpected query: "+sql);
                }
                return extractor.extractData(result);
            } catch(SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public int update(String sql,Object... args) {
            updates.add(new Update(sql,args));return 1;
        }

        private boolean containsUpdate(String fragment) {
            return updates.stream().anyMatch(update->update.sql().contains(fragment));
        }

        private long countUpdates(String fragment) {
            return updates.stream().filter(update->update.sql().contains(fragment)).count();
        }

        private Object[] argsFor(String fragment) {
            return updates.stream().filter(update->update.sql().contains(fragment)).findFirst()
                    .orElseThrow().args();
        }

        private static Evidence evidence(UUID turnId) {
            return Map.of(CORE_TURN,new Evidence(CORE_SECTION,CORE_QUESTION),
                    ENGLISH_TURN,new Evidence(ENGLISH_SECTION,ENGLISH_QUESTION)).get(turnId);
        }

        private record Update(String sql,Object[] args) {}
        private record Evidence(UUID sectionId,UUID questionId) {}
    }
}
