package com.cacanode.api.recruitment.migration;

import com.cacanode.api.testsupport.PostgresTestContainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class V38CvJobFitMigrationTest {
    private static String url;private static UUID tenant,application,cv;

    @BeforeAll static void migrate() throws Exception {
        url=PostgresTestContainer.createDatabase("v38_cv_job_fit");
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").target("37").load().migrate();
        seedLegacyRows();
        Flyway.configure().dataSource(url,PostgresTestContainer.username(),PostgresTestContainer.password())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test void backfillsLegacyContractsAndSequentialRevisions() throws Exception {
        try(Connection c=connection();Statement s=c.createStatement();ResultSet r=s.executeQuery("""
                SELECT contract_version,analysis_revision FROM recruitment_cv_analyses
                ORDER BY analysis_revision
                """)){
            assertTrue(r.next());assertEquals("1.1",r.getString(1));assertEquals(1,r.getInt(2));
            assertTrue(r.next());assertEquals("1.1",r.getString(1));assertEquals(2,r.getInt(2));
        }
    }

    @Test void enforcesRevisionRefreshIdAndCompletedFitConstraints() throws Exception {
        UUID refresh=UUID.randomUUID();insertV12(3,refresh,"FAILED",false);
        assertThrows(SQLException.class,()->insertV12(3,UUID.randomUUID(),"FAILED",false));
        assertThrows(SQLException.class,()->insertV12(4,refresh,"FAILED",false));
        assertThrows(SQLException.class,()->insertV12(5,UUID.randomUUID(),"COMPLETED",false));
        insertV12(5,UUID.randomUUID(),"COMPLETED",true);
    }

    private static void seedLegacyRows() throws Exception {
        tenant=UUID.randomUUID();application=UUID.randomUUID();cv=UUID.randomUUID();UUID job=UUID.randomUUID();
        String snapshot="{\"introductionText\":\"i\",\"disclosureText\":\"d\",\"closingText\":\"c\",\"durationLimitSeconds\":60,\"interactionLimits\":{\"repetitionLimit\":1,\"clarificationLimit\":1,\"silenceTimeoutSeconds\":10,\"silencePromptLimit\":1},\"sections\":[{}]}";
        try(Connection c=connection();Statement s=c.createStatement()){
            s.execute("SET session_replication_role=replica");
            try(PreparedStatement p=c.prepareStatement("""
                    INSERT INTO recruitment_applications(id,tenant_id,job_id,candidate_id,status,verified_at,locale,
                        privacy_consent_at,cv_use_disclosed_at,cv_present,cv_analysis_status,template_revision_id,
                        template_snapshot,template_snapshot_sha256,template_snapshot_version,cv_ai_mode_snapshot,
                        cv_ai_consent_at,cv_ai_policy_version,cv_ai_model_version)
                    VALUES (?,?,?,?,'SUBMITTED',NOW(),'en-US',NOW(),NOW(),true,'COMPLETED',?,?::jsonb,?,'1',
                        'PERSONALIZED_QUESTIONS',NOW(),'cv-redaction-v1','resume-analysis-v1')
                    """)){p.setObject(1,application);p.setObject(2,tenant);p.setObject(3,job);p.setObject(4,UUID.randomUUID());
                p.setObject(5,UUID.randomUUID());p.setString(6,snapshot);p.setString(7,"b".repeat(64));p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("""
                    INSERT INTO recruitment_application_cvs(id,tenant_id,application_id,job_id,original_filename,
                        content_type,byte_size,content_sha256,storage_state,promoted_object_key,storage_reservation_id,active)
                    VALUES (?,?,?,?,'cv.pdf','application/pdf',100,?,'PROMOTED','recruitment/cv',?,true)
                    """)){p.setObject(1,cv);p.setObject(2,tenant);p.setObject(3,application);p.setObject(4,job);
                p.setString(5,"a".repeat(64));p.setObject(6,UUID.randomUUID());p.executeUpdate();}
            insertLegacy(c,"resume-analysis-v1");insertLegacy(c,"resume-analysis-v1+pipeline-v2");
            s.execute("SET session_replication_role=origin");
        }
    }
    private static void insertLegacy(Connection c,String model) throws SQLException {try(PreparedStatement p=c.prepareStatement("""
            INSERT INTO recruitment_cv_analyses(id,tenant_id,application_id,cv_id,cv_sha256,analysis_mode,
                policy_version,model_version,status,request_event_id,request_payload,request_payload_sha256,next_publish_at)
            VALUES (?,?,?,?,?,'PERSONALIZED_QUESTIONS','cv-redaction-v1',?,'PUBLISHED',?,'{}'::jsonb,?,NOW())
            """)){p.setObject(1,UUID.randomUUID());p.setObject(2,tenant);p.setObject(3,application);p.setObject(4,cv);
        p.setString(5,"a".repeat(64));p.setString(6,model);p.setObject(7,UUID.randomUUID());p.setString(8,"c".repeat(64));p.executeUpdate();}}
    private static void insertV12(int revision,UUID refresh,String status,boolean fit)throws SQLException {try(Connection c=connection();PreparedStatement p=c.prepareStatement("""
            INSERT INTO recruitment_cv_analyses(id,tenant_id,application_id,cv_id,cv_sha256,analysis_mode,
                policy_version,model_version,contract_version,analysis_revision,refresh_request_id,status,
                request_event_id,request_payload,request_payload_sha256,next_publish_at,completed_at,failure_code,
                summary,fit_score_percent,fit_confidence,fit_explanation,strengths,gaps,outcome_event_id)
            VALUES (?,?,?,?,?,'PERSONALIZED_QUESTIONS','cv-redaction-fit-v2','resume-analysis-v2','1.2',?,?,?,
                ?,'{}'::jsonb,?,NOW(),CASE WHEN ? IN ('COMPLETED','FAILED') THEN NOW() END,
                CASE WHEN ?='FAILED' THEN 'FAILED' END,CASE WHEN ?='COMPLETED' THEN 'summary' END,
                CASE WHEN ? THEN 50 END,CASE WHEN ? THEN 'LOW' END,CASE WHEN ? THEN 'Advisory' END,
                CASE WHEN ? THEN '[{\"weight_percent\":100}]'::jsonb ELSE '[]'::jsonb END,'[]'::jsonb,
                CASE WHEN ?='COMPLETED' THEN ? END)
            """)){int i=1;p.setObject(i++,UUID.randomUUID());p.setObject(i++,tenant);p.setObject(i++,application);p.setObject(i++,cv);
        p.setString(i++,"a".repeat(64));p.setInt(i++,revision);p.setObject(i++,refresh);p.setString(i++,status);p.setObject(i++,UUID.randomUUID());
        p.setString(i++,"d".repeat(64));p.setString(i++,status);p.setString(i++,status);p.setString(i++,status);p.setBoolean(i++,fit);
        p.setBoolean(i++,fit);p.setBoolean(i++,fit);p.setBoolean(i++,fit);p.setString(i++,status);p.setObject(i,UUID.randomUUID());p.executeUpdate();}}
    private static Connection connection()throws SQLException{return DriverManager.getConnection(url,PostgresTestContainer.username(),PostgresTestContainer.password());}
}
