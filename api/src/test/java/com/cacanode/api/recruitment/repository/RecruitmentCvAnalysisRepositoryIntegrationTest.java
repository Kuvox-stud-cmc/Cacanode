package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentCvAnalysis;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CvAiMode;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CvAnalysisRecordStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties="spring.jpa.hibernate.ddl-auto=none")
@ActiveProfiles("test")
@Sql(statements="""
        CREATE DOMAIN IF NOT EXISTS JSONB AS JSON;
        DROP TABLE IF EXISTS recruitment_cv_analyses;
        CREATE TABLE recruitment_cv_analyses (
            id UUID PRIMARY KEY,
            tenant_id UUID NOT NULL,
            application_id UUID NOT NULL,
            cv_id UUID NOT NULL,
            cv_sha256 VARCHAR(64) NOT NULL,
            analysis_mode VARCHAR(30) NOT NULL,
            policy_version VARCHAR(80) NOT NULL,
            model_version VARCHAR(120) NOT NULL,
            contract_version VARCHAR(10) NOT NULL,
            analysis_revision INTEGER NOT NULL,
            refresh_request_id UUID,
            status VARCHAR(30) NOT NULL,
            request_event_id UUID,
            request_payload JSONB,
            request_payload_sha256 VARCHAR(64),
            publish_attempts INTEGER NOT NULL,
            next_publish_at TIMESTAMP,
            published_at TIMESTAMP,
            completed_at TIMESTAMP,
            failure_code VARCHAR(100),
            summary TEXT,
            evidence JSONB NOT NULL,
            skills JSONB NOT NULL,
            personalized_questions JSONB NOT NULL,
            fit_score_percent INTEGER,
            fit_confidence VARCHAR(10),
            fit_explanation TEXT,
            strengths JSONB NOT NULL,
            gaps JSONB NOT NULL,
            outcome_event_id UUID,
            outcome_payload_sha256 VARCHAR(64),
            version BIGINT NOT NULL,
            created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL,
            CONSTRAINT uq_recruitment_cv_analysis_revision UNIQUE
                (tenant_id,application_id,analysis_revision)
        );
        """)
class RecruitmentCvAnalysisRepositoryIntegrationTest {
    @Autowired private RecruitmentCvAnalysisRepository analyses;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsANewAnalysisWithItsDeterministicId() {
        UUID analysisId=UUID.randomUUID();
        RecruitmentCvAnalysis analysis=analysis(analysisId,UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),"resume-analysis-v1");

        analyses.saveAndFlush(analysis);
        entityManager.clear();

        RecruitmentCvAnalysis stored=analyses.findById(analysisId).orElseThrow();
        assertEquals(CvAnalysisRecordStatus.QUEUED,stored.getStatus());
        assertNotNull(stored.getVersion());
    }

    @Test
    void modelRevisionAllowsADurableRetryWithoutReplacingTheLegacyAttempt() {
        UUID tenantId=UUID.randomUUID(),applicationId=UUID.randomUUID(),cvId=UUID.randomUUID();
        RecruitmentCvAnalysis first=analysis(UUID.randomUUID(),tenantId,applicationId,cvId,"resume-analysis-v1");
        RecruitmentCvAnalysis second=analysis(UUID.randomUUID(),tenantId,applicationId,cvId,"resume-analysis-v1+pipeline-v2");
        second.setAnalysisRevision(2);analyses.saveAndFlush(first);analyses.saveAndFlush(second);

        assertEquals(2,analyses.count());
    }

    private RecruitmentCvAnalysis analysis(UUID analysisId,UUID tenantId,UUID applicationId,
            UUID cvId,String modelVersion) {
        RecruitmentCvAnalysis analysis=new RecruitmentCvAnalysis();
        analysis.setId(analysisId);analysis.setTenantId(tenantId);
        analysis.setApplicationId(applicationId);analysis.setCvId(cvId);
        analysis.setCvSha256("a".repeat(64));analysis.setAnalysisMode(CvAiMode.PERSONALIZED_QUESTIONS);
        analysis.setPolicyVersion("cv-redaction-v1");analysis.setModelVersion(modelVersion);
        analysis.setStatus(CvAnalysisRecordStatus.QUEUED);analysis.setRequestEventId(UUID.randomUUID());
        analysis.setRequestPayload("{}");analysis.setRequestPayloadSha256("b".repeat(64));
        analysis.setNextPublishAt(LocalDateTime.parse("2026-07-27T10:00:00"));
        return analysis;
    }
}
