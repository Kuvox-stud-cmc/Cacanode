package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V39EnglishBandWidthMigrationTest {
    private static final UUID TENANT=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID APPLICATION=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID INTERVIEW=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static String url;

    @BeforeAll
    static void migrate() throws Exception {
        url=PostgresTestContainer.createDatabase("v39_english_band_width");
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").target("38").load().migrate();
        seedLegacyWidthRows();
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test
    void widensBothMaterializedEnglishBandColumnsForEveryContractValue() throws Exception {
        assertEquals(30,columnLength("recruitment_applications"));
        assertEquals(30,columnLength("recruitment_interviews"));

        for(String band:List.of("BASIC","CONVERSATIONAL","WORKING_PROFICIENCY","PROFESSIONAL")) {
            assertEquals(band,updateAndRead("recruitment_applications",APPLICATION,band));
            assertEquals(band,updateAndRead("recruitment_interviews",INTERVIEW,band));
        }
    }

    private static void seedLegacyWidthRows() throws Exception {
        UUID job=UUID.randomUUID(),candidate=UUID.randomUUID(),revision=UUID.randomUUID();
        String snapshot="""
                {"introductionText":"i","disclosureText":"d","closingText":"c",
                 "durationLimitSeconds":60,"interactionLimits":{"repetitionLimit":1,
                 "clarificationLimit":1,"silenceTimeoutSeconds":10,"silencePromptLimit":1},
                 "sections":[{}]}
                """;
        try(Connection connection=connection();Statement statement=connection.createStatement()) {
            statement.execute("SET session_replication_role=replica");
            try(PreparedStatement insert=connection.prepareStatement("""
                    INSERT INTO recruitment_applications(id,tenant_id,job_id,candidate_id,status,verified_at,
                        locale,privacy_consent_at,template_revision_id,template_snapshot,
                        template_snapshot_sha256,template_snapshot_version,english_band)
                    VALUES (?,?,?,?,'INTERVIEW_SCHEDULED',NOW(),'vi-VN',NOW(),?,?::jsonb,?,'1','BASIC')
                    """)) {
                insert.setObject(1,APPLICATION);insert.setObject(2,TENANT);insert.setObject(3,job);
                insert.setObject(4,candidate);insert.setObject(5,revision);insert.setString(6,snapshot);
                insert.setString(7,"a".repeat(64));insert.executeUpdate();
            }
            try(PreparedStatement insert=connection.prepareStatement("""
                    INSERT INTO recruitment_interviews(id,tenant_id,application_id,job_id,status,
                        template_revision_id,template_snapshot,template_snapshot_sha256,
                        template_snapshot_version,english_band)
                    VALUES (?,?,?,?,'FAILED',?,?::jsonb,?,'1','BASIC')
                    """)) {
                insert.setObject(1,INTERVIEW);insert.setObject(2,TENANT);insert.setObject(3,APPLICATION);
                insert.setObject(4,job);insert.setObject(5,revision);insert.setString(6,snapshot);
                insert.setString(7,"a".repeat(64));insert.executeUpdate();
            }
            statement.execute("SET session_replication_role=origin");
        }
    }

    private static int columnLength(String table) throws Exception {
        try(Connection connection=connection();PreparedStatement statement=connection.prepareStatement("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema='public' AND table_name=? AND column_name='english_band'
                """)) {
            statement.setString(1,table);
            try(ResultSet result=statement.executeQuery()) {
                result.next();return result.getInt(1);
            }
        }
    }

    private static String updateAndRead(String table,UUID id,String band) throws Exception {
        try(Connection connection=connection();PreparedStatement update=connection.prepareStatement(
                "UPDATE "+table+" SET english_band=? WHERE id=?")) {
            update.setString(1,band);update.setObject(2,id);update.executeUpdate();
        }
        try(Connection connection=connection();PreparedStatement select=connection.prepareStatement(
                "SELECT english_band FROM "+table+" WHERE id=?")) {
            select.setObject(1,id);
            try(ResultSet result=select.executeQuery()) {
                result.next();return result.getString(1);
            }
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(url,PostgresTestContainer.username(),
                PostgresTestContainer.password());
    }
}
