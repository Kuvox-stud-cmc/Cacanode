package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentCandidateEmailDelivery;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CandidateEmailState;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CandidateEmailKind;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecruitmentCandidateEmailDeliveryRepository extends JpaRepository<RecruitmentCandidateEmailDelivery, UUID> {
    Optional<RecruitmentCandidateEmailDelivery> findByTenantIdAndDedupeKey(UUID tenantId, String dedupeKey);
    Optional<RecruitmentCandidateEmailDelivery> findFirstByTenantIdAndApplicationIdAndKindOrderByCreatedAtDesc(
            UUID tenantId,UUID applicationId,CandidateEmailKind kind);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from RecruitmentCandidateEmailDelivery d where d.id=:id")
    Optional<RecruitmentCandidateEmailDelivery> findForUpdate(@Param("id") UUID id);

    @Query(value="""
            SELECT * FROM recruitment_candidate_email_deliveries
            WHERE state IN ('PENDING','FAILED') AND attempts < 10 AND cancelled_at IS NULL
              AND due_at <= :now AND next_attempt_at <= :now
            ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED LIMIT 50
            """,nativeQuery=true)
    List<RecruitmentCandidateEmailDelivery> lockDue(@Param("now") LocalDateTime now);

    @Modifying
    @Query("update RecruitmentCandidateEmailDelivery d set d.state=:state,d.cancelledAt=:now where d.interviewId=:interviewId and d.sentAt is null and d.cancelledAt is null")
    int cancelInterview(@Param("interviewId") UUID interviewId, @Param("state") CandidateEmailState state,
                        @Param("now") LocalDateTime now);

    @Modifying
    @Query("update RecruitmentCandidateEmailDelivery d set d.state=:state,d.cancelledAt=:now where d.interviewId=:interviewId and d.kind=com.cacanode.api.recruitment.model.RecruitmentEnums.CandidateEmailKind.REMINDER and d.sentAt is null and d.cancelledAt is null")
    int cancelReminders(@Param("interviewId") UUID interviewId,@Param("state") CandidateEmailState state,
                        @Param("now") LocalDateTime now);
}
