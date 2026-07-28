package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentInterviewInvitationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RecruitmentInterviewInvitationTokenRepository extends JpaRepository<RecruitmentInterviewInvitationToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RecruitmentInterviewInvitationToken t where t.tokenHash=:hash")
    Optional<RecruitmentInterviewInvitationToken> findForUpdateByHash(@Param("hash") String hash);

    @Modifying
    @Query("update RecruitmentInterviewInvitationToken t set t.revokedAt=:now where t.interviewId=:interviewId and t.revokedAt is null")
    int revokeInterview(@Param("interviewId") UUID interviewId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update RecruitmentInterviewInvitationToken t set t.revokedAt=:now where t.deliveryId=:deliveryId and t.revokedAt is null")
    int revokeDelivery(@Param("deliveryId") UUID deliveryId,@Param("now") LocalDateTime now);
}
