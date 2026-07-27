package com.cacanode.api.recruitment.query;

import com.fasterxml.jackson.databind.ObjectMapper;
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
}
