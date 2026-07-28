package com.cacanode.api.recruitment.query;

import com.cacanode.api.tenant.api.TenantPublicProfileApi;
import com.cacanode.api.tenant.api.TenantStatus;
import com.cacanode.api.recruitment.config.RecruitmentActivationProperties;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class PublicJobProjectionService {
    private final NamedParameterJdbcTemplate jdbc;
    private final TenantPublicProfileApi tenantPublicProfileApi;
    private final RecruitmentProperties global;
    private final RecruitmentActivationProperties rollout;

    @Transactional
    public void synchronize(UUID tenantId, UUID jobId) {
        var profile = tenantPublicProfileApi.getPublicProfile(tenantId);
        ActivationGate gate=activation(tenantId);
        if ((profile.status()!=TenantStatus.ACTIVE && profile.status()!=TenantStatus.TRIAL) || !gate.masterEnabled()) {
            remove(tenantId,jobId); return;
        }
        boolean discoverable=gate.stage().equals("GA")&&gate.publicDiscoveryEnabled()&&rollout.gaUnlocked();
        boolean cvAiEnabled=gate.cvAiEnabled()&&global.cvAiEnabled();
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("jobId", jobId)
                .addValue("tenantSlug", profile.slug()).addValue("companyName", profile.companyName())
                .addValue("discoverable",discoverable).addValue("cvAiEnabled",cvAiEnabled);
        jdbc.update("""
                INSERT INTO recruitment_public_jobs(
                    job_id,tenant_id,public_id,tenant_slug,company_name,title,description,description_html,department,
                    location,employment_type,work_mode,experience_level,language,cv_policy,
                    cv_ai_disclosed,cv_ai_mode,screening_questions,published_at,closing_at,created_at,updated_at,discoverable)
                SELECT j.id,j.tenant_id,j.public_id,:tenantSlug,:companyName,j.title,j.description,j.description_html,j.department,
                    j.location,j.employment_type,j.work_mode,j.experience_level,j.language,j.cv_policy,
                    :cvAiEnabled AND j.effective_cv_ai_mode <> 'OFF',
                    CASE WHEN :cvAiEnabled THEN COALESCE(j.effective_cv_ai_mode,'OFF') ELSE 'OFF' END,
                    (SELECT COALESCE(jsonb_agg(jsonb_build_object('questionId',q->>'questionId','prompt',q->>'prompt','options',q->'options')),'[]'::jsonb)
                     FROM jsonb_array_elements(j.screening_config) q),
                    j.published_at,j.closing_at,j.created_at,NOW(),:discoverable
                FROM recruitment_jobs j
                WHERE j.tenant_id=:tenantId AND j.id=:jobId AND j.status='PUBLISHED' AND j.closing_at>NOW()
                ON CONFLICT (job_id) DO UPDATE SET
                    tenant_slug=EXCLUDED.tenant_slug,company_name=EXCLUDED.company_name,
                    title=EXCLUDED.title,description=EXCLUDED.description,description_html=EXCLUDED.description_html,department=EXCLUDED.department,
                    location=EXCLUDED.location,employment_type=EXCLUDED.employment_type,
                    work_mode=EXCLUDED.work_mode,experience_level=EXCLUDED.experience_level,
                    language=EXCLUDED.language,cv_policy=EXCLUDED.cv_policy,
                    cv_ai_disclosed=EXCLUDED.cv_ai_disclosed,cv_ai_mode=EXCLUDED.cv_ai_mode,
                    screening_questions=EXCLUDED.screening_questions,
                    published_at=EXCLUDED.published_at,
                    closing_at=EXCLUDED.closing_at,discoverable=EXCLUDED.discoverable,
                    version=recruitment_public_jobs.version+1,updated_at=NOW()
                """, params);
    }

    @Transactional
    public void remove(UUID tenantId, UUID jobId) {
        jdbc.update("DELETE FROM recruitment_public_jobs WHERE tenant_id=:tenantId AND job_id=:jobId",
                new MapSqlParameterSource("tenantId", tenantId).addValue("jobId", jobId));
    }

    @Transactional
    public void refreshTenant(UUID tenantId) {
        var profile = tenantPublicProfileApi.getPublicProfile(tenantId);
        if (profile.status()!=TenantStatus.ACTIVE && profile.status()!=TenantStatus.TRIAL) {
            jdbc.update("DELETE FROM recruitment_public_jobs WHERE tenant_id=:tenantId",
                    new MapSqlParameterSource("tenantId",tenantId)); return;
        }
        jdbc.update("""
                UPDATE recruitment_public_jobs SET tenant_slug=:slug,company_name=:name,
                    version=version+1,updated_at=NOW() WHERE tenant_id=:tenantId
                """, new MapSqlParameterSource("tenantId", tenantId)
                .addValue("slug", profile.slug()).addValue("name", profile.companyName()));
    }

    @Scheduled(fixedDelayString="${app.recruitment.public.projection-cleanup-ms:300000}")
    @Transactional
    public void removeExpired(){jdbc.update("DELETE FROM recruitment_public_jobs WHERE closing_at<=NOW()",java.util.Map.of());}

    private ActivationGate activation(UUID tenantId) {
        return jdbc.query("""
                SELECT rollout_stage,master_enabled,cv_ai_enabled,public_discovery_enabled
                FROM recruitment_tenant_activation WHERE tenant_id=:tenantId
                """,new MapSqlParameterSource("tenantId",tenantId),(rs,n)->new ActivationGate(
                rs.getString(1),rs.getBoolean(2),rs.getBoolean(3),rs.getBoolean(4)))
                .stream().findFirst().orElse(new ActivationGate("OFF",false,false,false));
    }
    private record ActivationGate(String stage,boolean masterEnabled,boolean cvAiEnabled,boolean publicDiscoveryEnabled){}
}
