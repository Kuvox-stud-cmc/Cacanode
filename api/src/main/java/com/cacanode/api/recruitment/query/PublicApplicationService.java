package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.recruitment.api.event.CandidateAccessEmailRequestedEvent;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.config.CvAnalysisProperties;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.model.RecruitmentApplication;
import com.cacanode.api.recruitment.model.RecruitmentApplicationEmailToken;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationEmailTokenRepository;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import com.cacanode.api.recruitment.service.PublicApplicationRateLimiter;
import com.cacanode.api.recruitment.service.RecruitmentTokenSupport;
import com.cacanode.api.recruitment.service.TurnstileVerifier;
import com.cacanode.api.recruitment.service.ScreeningSupport;
import com.cacanode.api.recruitment.service.RecruitmentProjectionEventPublisher;
import com.cacanode.api.recruitment.service.RecruitmentCapabilityService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class PublicApplicationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentApplicationEmailTokenRepository emailTokens;
    private final RecruitmentCvStorageService cvs;
    private final PublicApplicationRateLimiter limiter;
    private final TurnstileVerifier turnstile;
    private final RecruitmentTokenSupport tokens;
    private final PublicRecruitmentProperties properties;
    private final CvAnalysisProperties cvAnalysisProperties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ScreeningSupport screening;
    @Autowired(required=false) private RecruitmentProjectionEventPublisher projectionEvents;
    @Autowired(required=false) private RecruitmentCapabilityService capabilities;

    @Transactional
    public PublicRecruitmentDtos.AcceptedApplication submit(UUID publicId,
            PublicRecruitmentDtos.ApplicationData data, MultipartFile cv, String turnstileToken, String remoteIp) {
        JobSnapshot job=job(publicId);
        if(capabilities!=null)capabilities.requireMasterEnabled(job.tenantId());
        limiter.requireApplicationAttempt(publicId,remoteIp);
        if (!turnstile.verify(turnstileToken,remoteIp)) throw new BadRequestException("Application verification failed");
        validateCvPolicy(job.cvPolicy(),cv);
        boolean cvAttached=cv!=null&&!cv.isEmpty();
        boolean cvAiApplies=cvAttached&&job.cvAiMode()!=CvAiMode.OFF;
        if (cvAiApplies && !data.cvUseConsent()) throw new BadRequestException("CV-use consent is required");
        String screeningAnswers=screening.validateAnswers(job.screeningConfig(),data.screeningAnswers());
        RecruitmentCvStorageService.StagedCv staged=cv==null||cv.isEmpty()?null:cvs.stage(job.tenantId(),job.jobId(),cv);
        try {
            String email=data.email().strip().toLowerCase(Locale.ROOT);
            UUID candidateId=upsertCandidate(job.tenantId(),data,email);
            UUID proposedApplicationId=UUID.randomUUID();
            LocalDateTime now=now();
            var params=new MapSqlParameterSource().addValue("id",proposedApplicationId)
                    .addValue("tenantId",job.tenantId()).addValue("jobId",job.jobId()).addValue("candidateId",candidateId)
                    .addValue("submittedAt",now).addValue("locale",data.locale()).addValue("privacyAt",now)
                    .addValue("cvDisclosedAt",cvAiApplies?now:null).addValue("cvConsentAt",cvAiApplies?now:null)
                    .addValue("cvPresent",staged!=null).addValue("cvAiMode",job.cvAiMode().name())
                    .addValue("cvAiPolicyVersion",cvAnalysisProperties.policyVersion())
                    .addValue("cvAiModelVersion",cvAnalysisProperties.modelVersion())
                    .addValue("revisionId",job.templateRevisionId()).addValue("snapshot",job.templateSnapshot())
                    .addValue("snapshotHash",job.templateSnapshotSha256()).addValue("snapshotVersion",job.templateSnapshotVersion())
                    .addValue("screeningConfig",job.screeningConfig()).addValue("screeningAnswers",screeningAnswers)
                    .addValue("automationMode",job.automationMode().name());
            List<UUID> inserted=jdbc.query("""
                    INSERT INTO recruitment_applications(id,tenant_id,job_id,candidate_id,status,submitted_at,
                        locale,privacy_consent_at,cv_use_disclosed_at,cv_ai_consent_at,cv_present,cv_analysis_status,
                        cv_ai_mode_snapshot,cv_ai_policy_version,cv_ai_model_version,
                        template_revision_id,template_snapshot,template_snapshot_sha256,template_snapshot_version,
                        screening_config_snapshot,screening_answers,automation_mode_snapshot,automation_outcome)
                    VALUES(:id,:tenantId,:jobId,:candidateId,'SUBMITTED_UNVERIFIED',:submittedAt,:locale,
                        :privacyAt,:cvDisclosedAt,:cvConsentAt,:cvPresent,'NOT_REQUESTED',:cvAiMode,
                        :cvAiPolicyVersion,:cvAiModelVersion,:revisionId,CAST(:snapshot AS jsonb),
                        :snapshotHash,:snapshotVersion,CAST(:screeningConfig AS jsonb),CAST(:screeningAnswers AS jsonb),
                        :automationMode,'PENDING')
                    ON CONFLICT (tenant_id,job_id,candidate_id) DO NOTHING RETURNING id
                    """,params,(rs,n)->rs.getObject(1,UUID.class));
            boolean created=!inserted.isEmpty();
            UUID applicationId=created?inserted.getFirst():jdbc.queryForObject("""
                    SELECT id FROM recruitment_applications
                    WHERE tenant_id=:tenantId AND job_id=:jobId AND candidate_id=:candidateId
                    """,params,UUID.class);
            RecruitmentApplication application=applications.findForUpdate(job.tenantId(),applicationId)
                    .orElseThrow(()->new ResourceNotFoundException("Application was not found"));
            if(created&&projectionEvents!=null)projectionEvents.application(application,null);
            if (created && staged!=null) cvs.promote(staged,applicationId); else if(staged!=null)cvs.discard(staged);
            EmailTokenPurpose purpose=application.getStatus()==ApplicationStatus.SUBMITTED_UNVERIFIED
                    ?EmailTokenPurpose.VERIFICATION:EmailTokenPurpose.MANAGEMENT;
            emailTokens.revokeActive(applicationId,now);
            String raw=tokens.opaqueToken();
            RecruitmentApplicationEmailToken token=new RecruitmentApplicationEmailToken();
            token.setTenantId(job.tenantId());token.setApplicationId(applicationId);token.setJobId(job.jobId());
            token.setPurpose(purpose);token.setTokenHash(tokens.hash(raw));
            token.setExpiresAt(now.plusHours(purpose==EmailTokenPurpose.VERIFICATION?24:24*30L));
            emailTokens.saveAndFlush(token);
            if(limiter.allowEmailDelivery(job.jobId(),email)) events.publishEvent(new CandidateAccessEmailRequestedEvent(
                    email,data.fullName().strip(),job.companyName(),job.title(),data.locale(),
                    properties.candidateBaseUrl()+"#token="+raw,purpose==EmailTokenPurpose.VERIFICATION));
            return PublicRecruitmentDtos.AcceptedApplication.generic();
        } catch (RuntimeException exception) {
            if(staged!=null)cvs.discard(staged); throw exception;
        }
    }

    private UUID upsertCandidate(UUID tenantId,PublicRecruitmentDtos.ApplicationData data,String email){
        UUID proposed=UUID.randomUUID();
        var p=new MapSqlParameterSource().addValue("id",proposed).addValue("tenantId",tenantId)
                .addValue("fullName",data.fullName().strip()).addValue("normalizedName",normalize(data.fullName()))
                .addValue("email",email).addValue("phone",data.phone());
        List<UUID> inserted=jdbc.query("""
                INSERT INTO recruitment_candidates(id,tenant_id,full_name,normalized_name,email,normalized_email,phone)
                VALUES(:id,:tenantId,:fullName,:normalizedName,:email,:email,:phone)
                ON CONFLICT (tenant_id,normalized_email) DO NOTHING RETURNING id
                """,p,(rs,n)->rs.getObject(1,UUID.class));
        return inserted.isEmpty()?jdbc.queryForObject("SELECT id FROM recruitment_candidates WHERE tenant_id=:tenantId AND normalized_email=:email",p,UUID.class):inserted.getFirst();
    }

    private JobSnapshot job(UUID publicId){
        String sql="""
                SELECT p.job_id,p.tenant_id,p.company_name,p.title,p.cv_policy,p.cv_ai_mode,
                    j.template_revision_id,j.screening_config::text screening_config,
                    COALESCE(j.effective_automation_mode,'MANUAL') automation_mode,
                    r.content::text template_snapshot,r.content_sha256,
                    r.revision_number::text snapshot_version
                FROM recruitment_public_jobs p
                JOIN recruitment_jobs j ON j.tenant_id=p.tenant_id AND j.id=p.job_id
                JOIN recruitment_interview_template_revisions r ON r.tenant_id=j.tenant_id AND r.id=j.template_revision_id
                WHERE p.public_id=:publicId AND p.closing_at>:now
                """;
        return jdbc.query(sql,new MapSqlParameterSource("publicId",publicId).addValue("now",now()),(r,n)->new JobSnapshot(
                r.getObject("tenant_id",UUID.class),r.getObject("job_id",UUID.class),r.getString("company_name"),
                r.getString("title"),CvPolicy.valueOf(r.getString("cv_policy")),CvAiMode.valueOf(r.getString("cv_ai_mode")),
                r.getObject("template_revision_id",UUID.class),r.getString("template_snapshot"),
                r.getString("content_sha256"),r.getString("snapshot_version"),r.getString("screening_config"),
                AutomationMode.valueOf(r.getString("automation_mode")))).stream().findFirst()
                .orElseThrow(()->new ResourceNotFoundException("Public job was not found"));
    }
    private static void validateCvPolicy(CvPolicy policy,MultipartFile cv){boolean present=cv!=null&&!cv.isEmpty();if(policy==CvPolicy.DISABLED&&present)throw new BadRequestException("This job does not accept CV files");if(policy==CvPolicy.REQUIRED&&!present)throw new BadRequestException("A CV file is required");}
    private static String normalize(String value){return Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT),Normalizer.Form.NFKC).replaceAll("\\s+"," ");}
    private LocalDateTime now(){return LocalDateTime.now(clock);}
    private record JobSnapshot(UUID tenantId,UUID jobId,String companyName,String title,CvPolicy cvPolicy,
            CvAiMode cvAiMode,UUID templateRevisionId,String templateSnapshot,String templateSnapshotSha256,
            String templateSnapshotVersion,String screeningConfig,AutomationMode automationMode){}
}
