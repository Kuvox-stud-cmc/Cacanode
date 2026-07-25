package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class V29CvAnalysisMigrationTest {
    private static String url;private static UUID tenant,application,cv;

    @BeforeAll static void migrate() throws Exception {
        url=PostgresTestContainer.createDatabase("phase6_migration");
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("28")).load().migrate();
        seed();
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void convertsModesBackfillsConsentedSnapshotAndCreatesOwnedSchema() throws Exception {
        try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery("""
                SELECT a.cv_ai_mode_snapshot,a.cv_ai_consent_at,j.effective_cv_ai_mode
                FROM recruitment_applications a JOIN recruitment_jobs j ON j.id=a.job_id
                WHERE a.id='"""+application+"'")){assertTrue(r.next());assertEquals("PERSONALIZED_QUESTIONS",r.getString(1));assertNotNull(r.getTimestamp(2));assertEquals("PERSONALIZED_QUESTIONS",r.getString(3));}
        assertTrue(names("SELECT tablename FROM pg_tables WHERE schemaname='public'").containsAll(Set.of(
                "recruitment_cv_analyses","recruitment_cv_analysis_inbox")));
        assertTrue(names("SELECT indexname FROM pg_indexes").containsAll(Set.of(
                "uq_recruitment_cv_analysis_application_identity","idx_recruitment_cv_analysis_due")));
    }

    @Test void enforcesSemanticIdentityJsonBoundsCompositeBindingsAndImmutableSnapshots() throws Exception {
        UUID analysis=UUID.randomUUID(),event=UUID.randomUUID();String hash="a".repeat(64);
        String insert="""
                INSERT INTO recruitment_cv_analyses(id,tenant_id,application_id,cv_id,cv_sha256,analysis_mode,
                    policy_version,model_version,status,request_event_id,request_payload,request_payload_sha256,next_publish_at)
                VALUES (?,?,?,?,?,'PERSONALIZED_QUESTIONS','cv-redaction-v1','resume-analysis-v1','QUEUED',?,?::jsonb,?,NOW())
                """;
        try(Connection c=connection();PreparedStatement p=c.prepareStatement(insert)){p.setObject(1,analysis);p.setObject(2,tenant);p.setObject(3,application);p.setObject(4,cv);p.setString(5,hash);p.setObject(6,event);p.setString(7,"{}");p.setString(8,hash);p.executeUpdate();}
        assertThrows(SQLException.class,()->{try(Connection c=connection();PreparedStatement p=c.prepareStatement(insert)){p.setObject(1,UUID.randomUUID());p.setObject(2,tenant);p.setObject(3,application);p.setObject(4,cv);p.setString(5,hash);p.setObject(6,UUID.randomUUID());p.setString(7,"{}");p.setString(8,hash);p.executeUpdate();}});
        assertThrows(SQLException.class,()->{try(Connection c=connection();Statement s=c.createStatement()){s.execute("UPDATE recruitment_applications SET cv_ai_model_version='changed' WHERE id='"+application+"'");}});
        assertThrows(SQLException.class,()->{try(Connection c=connection();Statement s=c.createStatement()){s.execute("UPDATE recruitment_cv_analyses SET personalized_questions='[{},{},{}]'::jsonb WHERE id='"+analysis+"'");}});
    }

    private static void seed() throws Exception {
        tenant=UUID.randomUUID();UUID template=UUID.randomUUID(),revision=UUID.randomUUID(),job=UUID.randomUUID(),candidate=UUID.randomUUID();application=UUID.randomUUID();cv=UUID.randomUUID();
        String json="{\"introductionText\":\"i\",\"disclosureText\":\"d\",\"closingText\":\"c\",\"durationLimitSeconds\":60,\"interactionLimits\":{\"repetitionLimit\":1,\"clarificationLimit\":1,\"silenceTimeoutSeconds\":10,\"silencePromptLimit\":1},\"sections\":[{}]}";
        try(Connection c=connection()){
            c.createStatement().execute("INSERT INTO tenants(id,name,slug,plan,status,max_documents,max_messages,max_storage_mb,created_at,updated_at) VALUES ('"+tenant+"','Phase 6','phase-6','PRO','ACTIVE',10,100,1000,NOW(),NOW())");
            c.createStatement().execute("INSERT INTO recruitment_tenant_settings(tenant_id,default_automation_mode,cv_ai_mode) VALUES ('"+tenant+"','MANUAL','REQUIRED')");
            c.createStatement().execute("INSERT INTO recruitment_interview_templates(id,tenant_id,name,locale) VALUES ('"+template+"','"+tenant+"','Default','en-US')");
            try(PreparedStatement p=c.prepareStatement("INSERT INTO recruitment_interview_template_revisions(id,tenant_id,template_id,revision_number,content,content_sha256) VALUES (?,?,?,?,?::jsonb,?)")){p.setObject(1,revision);p.setObject(2,tenant);p.setObject(3,template);p.setInt(4,1);p.setString(5,json);p.setString(6,"b".repeat(64));p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO recruitment_jobs(id,tenant_id,public_id,title,description,language,status,cv_policy,effective_automation_mode,effective_cv_ai_mode,template_revision_id,closing_at,published_at,active_job_reservation_id,frozen_company_name,frozen_company_slug) VALUES (?,?,?,?,?,?,'PUBLISHED','OPTIONAL','MANUAL','REQUIRED',?,?,?,?,?,?)")){p.setObject(1,job);p.setObject(2,tenant);p.setObject(3,UUID.randomUUID());p.setString(4,"Engineer");p.setString(5,"Build things");p.setString(6,"en-US");p.setObject(7,revision);p.setObject(8,LocalDateTime.now().plusDays(10));p.setObject(9,LocalDateTime.now().minusDays(1));p.setObject(10,UUID.randomUUID());p.setString(11,"Acme");p.setString(12,"acme");p.executeUpdate();}
            c.createStatement().execute("INSERT INTO recruitment_candidates(id,tenant_id,full_name,normalized_name,email,normalized_email) VALUES ('"+candidate+"','"+tenant+"','Candidate','candidate','candidate@example.com','candidate@example.com')");
            try(PreparedStatement p=c.prepareStatement("INSERT INTO recruitment_applications(id,tenant_id,job_id,candidate_id,status,submitted_at,verified_at,locale,privacy_consent_at,cv_use_disclosed_at,cv_present,cv_analysis_status,template_revision_id,template_snapshot,template_snapshot_sha256,template_snapshot_version) VALUES (?,?,?,?,'SUBMITTED',NOW(),NOW(),'en-US',NOW(),NOW(),true,'NOT_REQUESTED',?,?::jsonb,?,'1')")){p.setObject(1,application);p.setObject(2,tenant);p.setObject(3,job);p.setObject(4,candidate);p.setObject(5,revision);p.setString(6,json);p.setString(7,"b".repeat(64));p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO recruitment_application_cvs(id,tenant_id,application_id,job_id,original_filename,content_type,byte_size,content_sha256,storage_state,promoted_object_key,storage_reservation_id,active) VALUES (?,?,?,?,'cv.pdf','application/pdf',100,?,'PROMOTED','recruitment/cv',?,true)")){p.setObject(1,cv);p.setObject(2,tenant);p.setObject(3,application);p.setObject(4,job);p.setString(5,"a".repeat(64));p.setObject(6,UUID.randomUUID());p.executeUpdate();}
        }
    }
    private static Set<String> names(String sql)throws Exception{Set<String> values=new HashSet<>();try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())values.add(r.getString(1));}return values;}
    private static Connection connection()throws SQLException{return DriverManager.getConnection(url,PostgresTestContainer.username(),PostgresTestContainer.password());}
}
