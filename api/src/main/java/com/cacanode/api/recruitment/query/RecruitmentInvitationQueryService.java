package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.model.RecruitmentInterview;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class RecruitmentInvitationQueryService {
    private final NamedParameterJdbcTemplate jdbc;

    public Recipient recipient(UUID tenantId,UUID applicationId){
        return jdbc.query("""
                SELECT c.email,c.full_name,j.frozen_company_name,j.title,a.locale
                FROM recruitment_applications a JOIN recruitment_candidates c ON c.tenant_id=a.tenant_id AND c.id=a.candidate_id
                JOIN recruitment_jobs j ON j.tenant_id=a.tenant_id AND j.id=a.job_id
                WHERE a.tenant_id=:tenantId AND a.id=:applicationId
                """,params(tenantId,applicationId),(r,n)->new Recipient(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)))
                .stream().findFirst().orElseThrow(RecruitmentInvitationQueryService::unauthorized);
    }

    public PublicRecruitmentDtos.InvitationDetails details(RecruitmentInterview interview){
        return jdbc.query("""
                SELECT c.full_name,j.frozen_company_name,j.title FROM recruitment_applications a
                JOIN recruitment_candidates c ON c.tenant_id=a.tenant_id AND c.id=a.candidate_id
                JOIN recruitment_jobs j ON j.tenant_id=a.tenant_id AND j.id=a.job_id
                WHERE a.tenant_id=:tenantId AND a.id=:applicationId
                """,params(interview.getTenantId(),interview.getApplicationId()),(r,n)->new PublicRecruitmentDtos.InvitationDetails(
                interview.getId(),r.getString(2),r.getString(3),r.getString(1),interview.getStatus(),
                interview.getScheduledStartAt(),interview.getScheduledEndAt(),interview.getSchedulingTimezone(),
                interview.getInvitationExpiresAt(),interview.getRescheduleCount())).stream().findFirst()
                .orElseThrow(RecruitmentInvitationQueryService::unauthorized);
    }
    private static MapSqlParameterSource params(UUID tenantId,UUID applicationId){return new MapSqlParameterSource("tenantId",tenantId).addValue("applicationId",applicationId);}
    private static UnauthorizedException unauthorized(){return new UnauthorizedException("Invalid or expired interview invitation");}
    public record Recipient(String email,String name,String company,String job,String locale){}
}
