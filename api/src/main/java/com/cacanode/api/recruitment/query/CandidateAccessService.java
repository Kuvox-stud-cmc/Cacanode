package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.cacanode.api.recruitment.service.ApplicationLifecycle;
import com.cacanode.api.recruitment.service.RecruitmentTokenSupport;
import com.cacanode.api.recruitment.service.ApplicationSubmissionTransitionService;
import com.cacanode.api.recruitment.service.RecruitmentInterviewCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class CandidateAccessService {
    public static final String ACCESS_COOKIE="recruitment_access";
    public static final String REFRESH_COOKIE="recruitment_refresh";
    private final RecruitmentApplicationEmailTokenRepository emailTokens;
    private final RecruitmentCandidateSessionRepository sessions;
    private final RecruitmentApplicationRepository applications;
    private final ApplicationSubmissionTransitionService submissionTransition;
    private final RecruitmentCvStorageService cvs;
    private final RecruitmentInterviewCancellationService cancellations;
    private final RecruitmentTokenSupport tokens;
    private final PublicRecruitmentProperties properties;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional
    public PublicRecruitmentDtos.CandidateSessionResponse exchange(String rawToken,HttpServletResponse response){
        RecruitmentApplicationEmailToken token=emailTokens.findForUpdateByHash(tokens.hash(rawToken))
                .orElseThrow(CandidateAccessService::unauthorized);
        LocalDateTime now=now();
        if(token.getConsumedAt()!=null||token.getRevokedAt()!=null||!token.getExpiresAt().isAfter(now))throw unauthorized();
        RecruitmentApplication application=applications.findForUpdate(token.getTenantId(),token.getApplicationId())
                .orElseThrow(CandidateAccessService::unauthorized);
        if(!application.getJobId().equals(token.getJobId()))throw unauthorized();
        if(token.getPurpose()==EmailTokenPurpose.VERIFICATION){
            submissionTransition.verifyLocked(application);
        }else if(application.getStatus()==ApplicationStatus.SUBMITTED_UNVERIFIED){throw unauthorized();}
        token.setConsumedAt(now);emailTokens.save(token);
        return issue(application,response);
    }

    @Transactional
    public PublicRecruitmentDtos.CandidateSessionResponse refresh(String rawRefresh,HttpServletResponse response){
        RecruitmentCandidateSession current=sessions.findForUpdateByRefreshHash(tokens.hash(rawRefresh))
                .orElseThrow(CandidateAccessService::unauthorized);
        if(!current.getRefreshExpiresAt().isAfter(sessionNow()))throw unauthorized();
        current.setRevokedAt(sessionNow());sessions.save(current);
        RecruitmentApplication application=applications.findByIdAndTenantId(current.getApplicationId(),current.getTenantId())
                .orElseThrow(CandidateAccessService::unauthorized);
        return issue(application,response);
    }

    @Transactional(readOnly=true)
    public PublicRecruitmentDtos.CandidateApplication me(String rawAccess){return view(requireAccess(rawAccess));}

    @Transactional
    public PublicRecruitmentDtos.CandidateApplication withdraw(String rawAccess,String rawCsrf){
        RecruitmentCandidateSession session=requireAccess(rawAccess);requireCsrf(session,rawCsrf);
        return view(cancellations.withdraw(session.getTenantId(),session.getApplicationId()));
    }

    @Transactional(readOnly=true)
    public DeletionSubject authorizeDeletion(String rawAccess,String rawCsrf) {
        RecruitmentCandidateSession session=requireAccess(rawAccess);requireCsrf(session,rawCsrf);
        RecruitmentApplication application=applications.findByIdAndTenantId(session.getApplicationId(),session.getTenantId())
                .orElseThrow(CandidateAccessService::unauthorized);
        return new DeletionSubject(application.getTenantId(),application.getId(),application.getCandidateId());
    }

    @Transactional
    public void logout(String rawAccess,String rawCsrf,HttpServletResponse response){
        RecruitmentCandidateSession session=requireAccess(rawAccess);requireCsrf(session,rawCsrf);
        session.setRevokedAt(sessionNow());sessions.save(session);clearCookies(response);
    }

    public void clearCookies(HttpServletResponse response){
        response.addHeader(HttpHeaders.SET_COOKIE,cookie(ACCESS_COOKIE,"",Duration.ZERO,"/api/v1/public/applications").toString());
        response.addHeader(HttpHeaders.SET_COOKIE,cookie(REFRESH_COOKIE,"",Duration.ZERO,"/api/v1/public/applications/session").toString());
    }

    private PublicRecruitmentDtos.CandidateSessionResponse issue(RecruitmentApplication application,HttpServletResponse response){
        String access=tokens.opaqueToken(),refresh=tokens.opaqueToken(),csrf=tokens.opaqueToken();LocalDateTime now=sessionNow();
        RecruitmentCandidateSession session=new RecruitmentCandidateSession();session.setTenantId(application.getTenantId());
        session.setApplicationId(application.getId());session.setJobId(application.getJobId());
        session.setAccessTokenHash(tokens.hash(access));session.setRefreshTokenHash(tokens.hash(refresh));
        session.setCsrfTokenHash(tokens.hash(csrf));session.setAccessExpiresAt(now.plusMinutes(30));
        session.setRefreshExpiresAt(now.plusDays(30));sessions.saveAndFlush(session);
        response.addHeader(HttpHeaders.SET_COOKIE,cookie(ACCESS_COOKIE,access,Duration.ofMinutes(30),"/api/v1/public/applications").toString());
        response.addHeader(HttpHeaders.SET_COOKIE,cookie(REFRESH_COOKIE,refresh,Duration.ofDays(30),"/api/v1/public/applications/session").toString());
        return new PublicRecruitmentDtos.CandidateSessionResponse(csrf,view(application));
    }

    private RecruitmentCandidateSession requireAccess(String raw){
        if(raw==null||raw.isBlank())throw unauthorized();
        RecruitmentCandidateSession session=sessions.findByAccessTokenHashAndRevokedAtIsNull(tokens.hash(raw))
                .orElseThrow(CandidateAccessService::unauthorized);
        if(!session.getAccessExpiresAt().isAfter(sessionNow()))throw unauthorized();return session;
    }
    private void requireCsrf(RecruitmentCandidateSession session,String raw){
        if(raw==null||!MessageDigest.isEqual(tokens.hash(raw).getBytes(StandardCharsets.US_ASCII),
                session.getCsrfTokenHash().getBytes(StandardCharsets.US_ASCII)))throw unauthorized();
    }
    private PublicRecruitmentDtos.CandidateApplication view(RecruitmentCandidateSession session){
        RecruitmentApplication application=applications.findByIdAndTenantId(session.getApplicationId(),session.getTenantId())
                .orElseThrow(CandidateAccessService::unauthorized);return view(application);
    }
    private PublicRecruitmentDtos.CandidateApplication view(RecruitmentApplication application){
        String sql="""
                SELECT j.public_id,j.frozen_company_name,j.title FROM recruitment_jobs j
                WHERE j.tenant_id=:tenantId AND j.id=:jobId
                """;
        var p=new MapSqlParameterSource("tenantId",application.getTenantId()).addValue("jobId",application.getJobId());
        return jdbc.query(sql,p,(r,n)->new PublicRecruitmentDtos.CandidateApplication(application.getId(),
                r.getObject("public_id",java.util.UUID.class),r.getString("frozen_company_name"),r.getString("title"),
                application.getStatus(),application.getSubmittedAt(),application.getVerifiedAt(),application.getWithdrawnAt(),
                application.isCvPresent())).stream().findFirst().orElseThrow(CandidateAccessService::unauthorized);
    }
    private ResponseCookie cookie(String name,String value,Duration age,String path){return ResponseCookie.from(name,value)
            .httpOnly(true).secure(properties.cookieSecure()).sameSite("Strict").path(path).maxAge(age).build();}
    private LocalDateTime now(){return LocalDateTime.now(clock);}
    LocalDateTime sessionNow(){return LocalDateTime.ofInstant(clock.instant(),ZoneId.systemDefault());}
    private static UnauthorizedException unauthorized(){return new UnauthorizedException("Invalid or expired candidate access");}
    public record DeletionSubject(java.util.UUID tenantId,java.util.UUID applicationId,java.util.UUID candidateId) {}
}
