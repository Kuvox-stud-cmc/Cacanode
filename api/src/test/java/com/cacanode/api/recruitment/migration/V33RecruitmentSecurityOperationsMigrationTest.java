package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class V33RecruitmentSecurityOperationsMigrationTest {
    private static String url;
    @BeforeAll static void migrate(){url=PostgresTestContainer.createDatabase("phase11_migration");Flyway.configure()
            .dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
            .locations("classpath:db/migration").load().migrate();}

    @Test void defaultsEveryTenantOffAndInitializesFutureTenants() throws Exception {
        try(Connection connection=connect();Statement statement=connection.createStatement()) {
            ResultSet defaults=statement.executeQuery("SELECT count(*) FROM tenants t LEFT JOIN recruitment_tenant_activation a ON a.tenant_id=t.id WHERE a.tenant_id IS NULL OR a.rollout_stage<>'OFF' OR a.master_enabled");
            assertTrue(defaults.next());assertEquals(0,defaults.getLong(1));
            UUID id=UUID.randomUUID();statement.executeUpdate("INSERT INTO tenants(id,name,slug) VALUES ('"+id+"','Phase 11','phase-11-"+id+"')");
            ResultSet inserted=statement.executeQuery("SELECT rollout_stage,master_enabled FROM recruitment_tenant_activation WHERE tenant_id='"+id+"'");
            assertTrue(inserted.next());assertEquals("OFF",inserted.getString(1));assertFalse(inserted.getBoolean(2));
        }
    }

    @Test void enforcesSingleInternalPilotPlanAndGaDiscovery() throws Exception {
        try(Connection connection=connect();Statement statement=connection.createStatement()) {
            UUID first=tenant(statement,"internal-a"),second=tenant(statement,"internal-b");
            statement.executeUpdate("UPDATE recruitment_tenant_activation SET rollout_stage='INTERNAL',master_enabled=true WHERE tenant_id='"+first+"'");
            assertThrows(SQLException.class,()->statement.executeUpdate("UPDATE recruitment_tenant_activation SET rollout_stage='INTERNAL',master_enabled=true WHERE tenant_id='"+second+"'"));
            UUID starter=tenant(statement,"pilot-starter");
            assertThrows(SQLException.class,()->statement.executeUpdate("UPDATE recruitment_tenant_activation SET rollout_stage='PILOT',master_enabled=true WHERE tenant_id='"+starter+"'"));
        }
    }

    @Test void addsPrivacyMinimalLedgerDiscoveryDurationsStopAndDeletionPurpose() throws Exception {
        try(Connection connection=connect();Statement statement=connection.createStatement()) {
            assertTrue(column(statement,"recruitment_public_jobs","discoverable"));
            assertTrue(column(statement,"recruitment_interview_call_attempts","call_duration_seconds"));
            assertTrue(column(statement,"recruitment_interview_recordings","recording_duration_seconds"));
            ResultSet columns=statement.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='recruitment_privacy_deletion_requests'");
            while(columns.next())assertFalse(java.util.Set.of("email","phone","full_name","reason","transcript","storage_key").contains(columns.getString(1)));
        }
    }
    private static UUID tenant(Statement statement,String slug)throws Exception{UUID id=UUID.randomUUID();statement.executeUpdate("INSERT INTO tenants(id,name,slug,plan) VALUES ('"+id+"','Test','"+slug+"','STARTER')");return id;}
    private static boolean column(Statement statement,String table,String column)throws Exception{ResultSet rs=statement.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_name='"+table+"' AND column_name='"+column+"'");rs.next();return rs.getInt(1)==1;}
    private static Connection connect()throws Exception{Connection value=DriverManager.getConnection(url,PostgresTestContainer.username(),PostgresTestContainer.password());value.setAutoCommit(true);return value;}
}
