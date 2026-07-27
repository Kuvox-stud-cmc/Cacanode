package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentEnums.CallAttemptStatus;
import com.cacanode.api.recruitment.model.RecruitmentInterviewCallAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecruitmentInterviewCallAttemptRepository extends JpaRepository<RecruitmentInterviewCallAttempt,UUID> {
    Optional<RecruitmentInterviewCallAttempt> findByIdAndTenantId(UUID id,UUID tenantId);
    Optional<RecruitmentInterviewCallAttempt> findByTwilioCallSid(String twilioCallSid);
    List<RecruitmentInterviewCallAttempt> findTop100ByTenantIdAndInterviewIdOrderByAttemptNumberDesc(
            UUID tenantId,UUID interviewId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RecruitmentInterviewCallAttempt a where a.id=:id")
    Optional<RecruitmentInterviewCallAttempt> findForUpdate(@Param("id") UUID id);

    @Query("select coalesce(max(a.attemptNumber),0) from RecruitmentInterviewCallAttempt a where a.interviewId=:interviewId")
    int maxAttemptNumber(@Param("interviewId") UUID interviewId);

    @Query("select count(a) from RecruitmentInterviewCallAttempt a where a.status in :statuses")
    long countGlobalActive(@Param("statuses") List<CallAttemptStatus> statuses);

    @Query("select count(a) from RecruitmentInterviewCallAttempt a where a.tenantId=:tenantId and a.status in :statuses")
    long countTenantActive(@Param("tenantId") UUID tenantId,@Param("statuses") List<CallAttemptStatus> statuses);

    @Query(value="""
            SELECT * FROM recruitment_interview_call_attempts
            WHERE create_outcome_uncertain=true AND create_uncertain_until<=:now
            ORDER BY create_uncertain_until,id FOR UPDATE SKIP LOCKED LIMIT 50
            """,nativeQuery=true)
    List<RecruitmentInterviewCallAttempt> lockExpiredUncertain(@Param("now") Instant now);

    @Modifying
    @Query(value="""
            UPDATE recruitment_interview_call_attempts
            SET status='CANCELLED',failure_code='INTERVIEW_CANCELLED',cancelled_at=:now,terminal_at=:now,
                next_retry_at=NULL,updated_at=NOW(),version=version+1
            WHERE tenant_id=:tenantId AND interview_id=:interviewId
              AND status IN ('PREPARING','READY','DIALING','CALLING','RINGING','CONSENT_PENDING','IN_PROGRESS')
            """,nativeQuery=true)
    int cancelActive(@Param("tenantId") UUID tenantId,@Param("interviewId") UUID interviewId,@Param("now") Instant now);
}
