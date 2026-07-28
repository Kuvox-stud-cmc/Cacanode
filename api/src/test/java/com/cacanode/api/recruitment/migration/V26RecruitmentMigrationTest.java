package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class V26RecruitmentMigrationTest {
    private static String jdbcUrl;
    private static UUID tenantOne;
    private static UUID tenantTwo;
    private static UUID tenantOneRevision;

    @BeforeAll
    static void migrateFromV25() throws SQLException {
        jdbcUrl = PostgresTestContainer.createDatabase("phase3_migration");
        Flyway.configure().dataSource(jdbcUrl, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("25")).load().migrate();
        tenantOne = tenant("phase3-one"); tenantTwo = tenant("phase3-two");
        Flyway.configure().dataSource(jdbcUrl, PostgresTestContainer.username(), PostgresTestContainer.password())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("26")).load().migrate();
        tenantOneRevision = revision(tenantOne);
    }

    @Test
    void createsAllOwnedTablesConstraintsAndStableIndexes() throws SQLException {
        assertEquals(Set.of("recruitment_tenant_settings","recruitment_jobs","recruitment_interview_templates",
                "recruitment_interview_template_revisions","recruitment_candidates","recruitment_applications",
                "recruitment_interviews"), names("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'recruitment_%'"));
        Set<String> constraints=names("SELECT conname FROM pg_constraint WHERE conname LIKE 'fk_recruitment_%' OR conname LIKE 'ck_recruitment_%'");
        assertTrue(constraints.containsAll(Set.of("fk_recruitment_job_revision","fk_recruitment_application_job",
                "fk_recruitment_application_candidate","fk_recruitment_interview_application",
                "ck_recruitment_revision_json","ck_recruitment_application_snapshot","ck_recruitment_interview_score")));
        Set<String> indexes=names("SELECT indexname FROM pg_indexes WHERE indexname LIKE 'idx_recruitment_%'");
        assertTrue(indexes.containsAll(Set.of("idx_recruitment_jobs_tenant_status_created_id",
                "idx_recruitment_jobs_search","idx_recruitment_candidates_search",
                "idx_recruitment_applications_tenant_status_submitted_id","idx_recruitment_interviews_score_id")));
    }

    @Test
    void rejectsCrossTenantReferencesBadModesHashesJsonAndRecording() {
        assertRejected("INSERT INTO recruitment_tenant_settings(tenant_id,default_automation_mode,cv_ai_mode,recording_enabled,recording_retention_days) VALUES ('"+tenantOne+"','UNKNOWN','OFF',false,0)");
        assertRejected("INSERT INTO recruitment_tenant_settings(tenant_id,default_automation_mode,cv_ai_mode,recording_enabled,recording_retention_days) VALUES ('"+tenantOne+"','MANUAL','OFF',true,0)");
        assertRejected("INSERT INTO recruitment_jobs(tenant_id,title,description,language,status,cv_policy,template_revision_id) VALUES ('"+tenantTwo+"','Job','Description','en-US','DRAFT','OPTIONAL','"+tenantOneRevision+"')");
        assertRejected("INSERT INTO recruitment_interview_template_revisions(tenant_id,template_id,revision_number,content,content_sha256) VALUES ('"+tenantOne+"',gen_random_uuid(),1,'[]','bad')");
    }

    private static UUID tenant(String slug)throws SQLException {UUID id=UUID.randomUUID();try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO tenants(id,name,slug,plan,status,max_documents,max_messages,max_storage_mb,created_at,updated_at) VALUES (?,? ,?,'PRO','ACTIVE',10,100,1000,NOW(),NOW())")){s.setObject(1,id);s.setString(2,slug);s.setString(3,slug);s.executeUpdate();}return id;}
    private static UUID revision(UUID tenant)throws SQLException {UUID template=UUID.randomUUID(),revision=UUID.randomUUID();String json="{\"introductionText\":\"i\",\"disclosureText\":\"d\",\"closingText\":\"c\",\"durationLimitSeconds\":60,\"interactionLimits\":{\"repetitionLimit\":1,\"clarificationLimit\":1,\"silenceTimeoutSeconds\":10,\"silencePromptLimit\":1},\"sections\":[{}]}";try(Connection c=connection();PreparedStatement t=c.prepareStatement("INSERT INTO recruitment_interview_templates(id,tenant_id,name,locale) VALUES (?,?,?,'en-US')");PreparedStatement r=c.prepareStatement("INSERT INTO recruitment_interview_template_revisions(id,tenant_id,template_id,revision_number,content,content_sha256) VALUES (?,?,?,?,?::jsonb,?)")){t.setObject(1,template);t.setObject(2,tenant);t.setString(3,"Template");t.executeUpdate();r.setObject(1,revision);r.setObject(2,tenant);r.setObject(3,template);r.setInt(4,1);r.setString(5,json);r.setString(6,"a".repeat(64));r.executeUpdate();}return revision;}
    private static void assertRejected(String sql){assertThrows(SQLException.class,()->{try(Connection c=connection();Statement s=c.createStatement()){s.execute(sql);}});}
    private static Set<String> names(String sql)throws SQLException {Set<String> result=new HashSet<>();try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())result.add(r.getString(1));}return result;}
    private static Connection connection()throws SQLException{return DriverManager.getConnection(jdbcUrl,PostgresTestContainer.username(),PostgresTestContainer.password());}
}
