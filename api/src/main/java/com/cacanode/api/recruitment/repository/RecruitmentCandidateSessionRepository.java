package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentCandidateSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RecruitmentCandidateSessionRepository extends JpaRepository<RecruitmentCandidateSession, UUID> {
    Optional<RecruitmentCandidateSession> findByAccessTokenHashAndRevokedAtIsNull(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RecruitmentCandidateSession s where s.refreshTokenHash=:hash and s.revokedAt is null")
    Optional<RecruitmentCandidateSession> findForUpdateByRefreshHash(@Param("hash") String hash);
    @Modifying
    @Query("update RecruitmentCandidateSession s set s.revokedAt=:now where s.applicationId=:applicationId and s.revokedAt is null")
    int revokeApplication(@Param("applicationId") UUID applicationId, @Param("now") LocalDateTime now);
}
