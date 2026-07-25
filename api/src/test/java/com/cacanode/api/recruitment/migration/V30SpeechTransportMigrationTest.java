package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class V30SpeechTransportMigrationTest {
    private static String url;

    @BeforeAll static void migrate() {
        url=PostgresTestContainer.createDatabase("phase7_migration");
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void createsOwnedAttemptsInboxBindingsAndDueIndexes() throws Exception {
        assertTrue(names("SELECT tablename FROM pg_tables WHERE schemaname='public'").containsAll(Set.of(
                "recruitment_interview_call_attempts","recruitment_twilio_callback_inbox")));
        assertTrue(names("SELECT indexname FROM pg_indexes WHERE schemaname='public'").containsAll(Set.of(
                "uq_recruitment_call_attempt_active","idx_recruitment_call_attempt_due",
                "idx_recruitment_call_attempt_reconcile","idx_recruitment_twilio_inbox_attempt")));
        Set<String> constraints=names("""
                SELECT conname FROM pg_constraint WHERE conrelid IN (
                    'recruitment_interview_call_attempts'::regclass,
                    'recruitment_twilio_callback_inbox'::regclass,
                    'recruitment_interviews'::regclass)
                """);
        assertTrue(constraints.containsAll(Set.of("uq_recruitment_call_attempt_number",
                "fk_recruitment_call_attempt_interview","fk_recruitment_interview_active_call_attempt",
                "fk_recruitment_twilio_inbox_attempt","ck_recruitment_call_attempt_json",
                "ck_recruitment_call_attempt_terminal")));
    }

    @Test void activeUniquenessIsPartialAndApplicationCascadeIsPreserved() throws Exception {
        try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery("""
                SELECT indexdef FROM pg_indexes
                WHERE indexname='uq_recruitment_call_attempt_active'
                """)) {
            assertTrue(r.next());String definition=r.getString(1);
            assertTrue(definition.contains("PREPARING"));assertTrue(definition.contains("IN_PROGRESS"));
            assertFalse(definition.contains("COMPLETED"));
        }
        try(Connection c=connection();PreparedStatement p=c.prepareStatement("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname='fk_recruitment_interview_application'
                """)) {
            try(ResultSet r=p.executeQuery()){assertTrue(r.next());assertTrue(r.getString(1).contains("ON DELETE CASCADE"));}
        }
    }

    private static Set<String> names(String sql)throws Exception {
        Set<String> values=new HashSet<>();try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){
            while(r.next())values.add(r.getString(1));}return values;
    }
    private static Connection connection()throws SQLException{return DriverManager.getConnection(
            url,PostgresTestContainer.username(),PostgresTestContainer.password());}
}
