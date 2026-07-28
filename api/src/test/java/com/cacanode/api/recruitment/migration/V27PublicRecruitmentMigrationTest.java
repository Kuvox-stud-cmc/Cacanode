package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class V27PublicRecruitmentMigrationTest {
    private static String jdbcUrl;

    @BeforeAll static void migrate() throws Exception {
        jdbcUrl=PostgresTestContainer.createDatabase("phase4_migration");
        Flyway.configure().dataSource(jdbcUrl,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("26")).load().migrate();
        seedPublishedJob();
        Flyway.configure().dataSource(jdbcUrl,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void createsOwnedTablesConstraintsIndexesAndBackfillsProjection() throws Exception {
        assertTrue(names("SELECT tablename FROM pg_tables WHERE schemaname='public'").containsAll(Set.of(
                "recruitment_public_jobs","recruitment_application_email_tokens",
                "recruitment_candidate_sessions","recruitment_application_cvs")));
        assertTrue(names("SELECT conname FROM pg_constraint").containsAll(Set.of(
                "fk_recruitment_public_job_job","fk_recruitment_email_token_application",
                "fk_recruitment_candidate_session_application","fk_recruitment_cv_application",
                "ck_recruitment_email_token_expiry","ck_recruitment_candidate_session_hashes")));
        assertTrue(names("SELECT indexname FROM pg_indexes").containsAll(Set.of(
                "idx_recruitment_public_jobs_search_vector","idx_recruitment_public_jobs_trigram",
                "uq_recruitment_active_cv_per_application","idx_recruitment_cv_cleanup")));
        try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT count(*) FROM recruitment_public_jobs")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
    }

    @Test void canonicalizesCvPolicyAndAddsControlledFacets() throws Exception {
        try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT cv_policy FROM recruitment_jobs")){assertTrue(r.next());assertEquals("DISABLED",r.getString(1));}
        assertThrows(SQLException.class,()->{try(Connection c=connection();Statement s=c.createStatement()){s.execute("UPDATE recruitment_jobs SET experience_level='UNKNOWN'");}});
    }

    private static void seedPublishedJob() throws Exception {
        UUID tenant=UUID.randomUUID(),template=UUID.randomUUID(),revision=UUID.randomUUID(),job=UUID.randomUUID(),reservation=UUID.randomUUID();
        String json="{\"introductionText\":\"i\",\"disclosureText\":\"d\",\"closingText\":\"c\",\"durationLimitSeconds\":60,\"interactionLimits\":{\"repetitionLimit\":1,\"clarificationLimit\":1,\"silenceTimeoutSeconds\":10,\"silencePromptLimit\":1},\"sections\":[{}]}";
        try(Connection c=connection()){
            c.createStatement().execute("INSERT INTO tenants(id,name,slug,plan,status,max_documents,max_messages,max_storage_mb,created_at,updated_at) VALUES ('"+tenant+"','Acme','acme','PRO','ACTIVE',10,100,1000,NOW(),NOW())");
            c.createStatement().execute("INSERT INTO recruitment_interview_templates(id,tenant_id,name,locale) VALUES ('"+template+"','"+tenant+"','Default','en-US')");
            try(PreparedStatement p=c.prepareStatement("INSERT INTO recruitment_interview_template_revisions(id,tenant_id,template_id,revision_number,content,content_sha256) VALUES (?,?,?,?,?::jsonb,?)")){p.setObject(1,revision);p.setObject(2,tenant);p.setObject(3,template);p.setInt(4,1);p.setString(5,json);p.setString(6,"a".repeat(64));p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO recruitment_jobs(id,tenant_id,public_id,title,description,language,status,cv_policy,effective_automation_mode,effective_cv_ai_mode,template_revision_id,closing_at,published_at,active_job_reservation_id,frozen_company_name,frozen_company_slug) VALUES (?,?,?,?,?,?,'PUBLISHED','NOT_ACCEPTED','MANUAL','OFF',?,?,?,?,?,?)")){p.setObject(1,job);p.setObject(2,tenant);p.setObject(3,UUID.randomUUID());p.setString(4,"Engineer");p.setString(5,"Build things");p.setString(6,"en-US");p.setObject(7,revision);p.setObject(8,LocalDateTime.now().plusDays(10));p.setObject(9,LocalDateTime.now().minusDays(1));p.setObject(10,reservation);p.setString(11,"Acme");p.setString(12,"acme");p.executeUpdate();}
        }
    }
    private static Set<String> names(String sql)throws Exception{Set<String> values=new HashSet<>();try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())values.add(r.getString(1));}return values;}
    private static Connection connection()throws SQLException{return DriverManager.getConnection(jdbcUrl,PostgresTestContainer.username(),PostgresTestContainer.password());}
}
