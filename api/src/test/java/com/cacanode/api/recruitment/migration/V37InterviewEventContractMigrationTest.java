package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class V37InterviewEventContractMigrationTest {
    private static final UUID TENANT=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID SESSION=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID FIRST_ATTEMPT=UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID SECOND_ATTEMPT=UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static String url;

    @BeforeAll
    static void migrateAcrossV37() throws Exception {
        url=PostgresTestContainer.createDatabase("v37_interview_events");
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").target("36").load().migrate();
        try(Connection connection=connection();Statement statement=connection.createStatement()) {
            statement.execute("SET session_replication_role=replica");
            statement.executeUpdate(insert(UUID.fromString("11111111-1111-4111-8111-111111111111"),
                    FIRST_ATTEMPT,"1.1","completed:v1.1"));
            statement.execute("SET session_replication_role=origin");
        }
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test
    void preservesLegacyRowsAndAllowsTheSameSemanticKeyAcrossAttempts() throws Exception {
        try(Connection connection=connection();Statement statement=connection.createStatement()) {
            assertEquals(1,count(statement));
            statement.execute("SET session_replication_role=replica");
            statement.executeUpdate(insert(UUID.fromString("22222222-1111-4111-8111-111111111111"),
                    SECOND_ATTEMPT,"1.2","completed:v1.1"));
            statement.execute("SET session_replication_role=origin");
            assertEquals(2,count(statement));
        }
    }

    @Test
    void rejectsDuplicateSemanticsWithinOneAttempt() throws Exception {
        try(Connection connection=connection();Statement statement=connection.createStatement()) {
            statement.execute("SET session_replication_role=replica");
            assertThrows(SQLException.class,()->statement.executeUpdate(insert(
                    UUID.fromString("33333333-1111-4111-8111-111111111111"),FIRST_ATTEMPT,
                    "1.2","completed:v1.1")));
        }
    }

    private static int count(Statement statement)throws SQLException {
        try(var result=statement.executeQuery("SELECT count(*) FROM recruitment_interview_event_inbox")) {
            result.next();return result.getInt(1);
        }
    }

    private static String insert(UUID eventId,UUID attemptId,String version,String semantic) {
        return """
                INSERT INTO recruitment_interview_event_inbox(event_id,tenant_id,session_id,call_attempt_id,
                    schema_version,event_type,semantic_key,payload_sha256,canonical_payload,
                    processing_status,occurred_at)
                VALUES ('%s','%s','%s','%s','%s','interview.session.completed','%s','%s',
                    '{}','APPLIED',NOW())
                """.formatted(eventId,TENANT,SESSION,attemptId,version,semantic,"a".repeat(64));
    }

    private static Connection connection()throws SQLException {
        return DriverManager.getConnection(url,PostgresTestContainer.username(),PostgresTestContainer.password());
    }
}
