package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentInterview;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public interface RecruitmentInterviewRepository extends JpaRepository<RecruitmentInterview, UUID> {
    Optional<RecruitmentInterview> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<RecruitmentInterview> findByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from RecruitmentInterview i where i.id = :id and i.tenantId = :tenantId")
    Optional<RecruitmentInterview> findForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from RecruitmentInterview i where i.applicationId=:applicationId and i.tenantId=:tenantId")
    Optional<RecruitmentInterview> findByApplicationForUpdate(@Param("tenantId") UUID tenantId,
                                                               @Param("applicationId") UUID applicationId);

    @Query("""
            select i from RecruitmentInterview i where i.tenantId=:tenantId
              and i.status in (com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus.SCHEDULED,
                com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus.PREPARING,
                com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus.CALLING,
                com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus.RINGING,
                com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus.CONSENT_PENDING,
                com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus.IN_PROGRESS)
              and i.scheduledStartAt < :end and i.scheduledEndAt > :start
            """)
    List<RecruitmentInterview> findOverlapping(@Param("tenantId") UUID tenantId,
                                                @Param("start") Instant start,@Param("end") Instant end);

    @Query(value="""
            SELECT * FROM recruitment_interviews WHERE status='INVITED' AND invitation_expires_at<=:now
            ORDER BY invitation_expires_at,id FOR UPDATE SKIP LOCKED LIMIT 100
            """,nativeQuery=true)
    List<RecruitmentInterview> lockExpiredInvitations(@Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from RecruitmentInterview i where i.tenantId=:tenantId and i.jobId=:jobId")
    List<RecruitmentInterview> findJobForUpdate(@Param("tenantId") UUID tenantId,@Param("jobId") UUID jobId);

    @Query(value="SELECT pg_try_advisory_xact_lock(746302007)",nativeQuery=true)
    boolean tryCallSchedulerLock();

    @Query(value="""
            SELECT * FROM recruitment_interviews
            WHERE status='SCHEDULED' AND active_call_attempt_id IS NULL
              AND scheduled_start_at BETWEEN :from AND :to
            ORDER BY scheduled_start_at,id FOR UPDATE SKIP LOCKED LIMIT 100
            """,nativeQuery=true)
    List<RecruitmentInterview> lockDueForCalling(@Param("from") Instant from,@Param("to") Instant to);

    @Query(value="""
            SELECT * FROM recruitment_interviews
            WHERE status='SCHEDULED' AND active_call_attempt_id IS NULL AND scheduled_start_at<:before
            ORDER BY scheduled_start_at,id FOR UPDATE SKIP LOCKED LIMIT 100
            """,nativeQuery=true)
    List<RecruitmentInterview> lockMissedForCalling(@Param("before") Instant before);

    @Query(value="""
            SELECT i.* FROM recruitment_interviews i
            JOIN recruitment_tenant_activation a ON a.tenant_id=i.tenant_id
            WHERE i.active_call_attempt_id IS NOT NULL
              AND (NOT a.master_enabled OR NOT a.calling_enabled OR a.rollout_stage='OFF')
            ORDER BY i.updated_at,i.id FOR UPDATE OF i SKIP LOCKED LIMIT 100
            """,nativeQuery=true)
    List<RecruitmentInterview> lockKillSwitchedCalls();
}
