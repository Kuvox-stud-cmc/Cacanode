package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentApplicationEmailToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import com.cacanode.api.recruitment.model.RecruitmentEnums.EmailTokenPurpose;

public interface RecruitmentApplicationEmailTokenRepository extends JpaRepository<RecruitmentApplicationEmailToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RecruitmentApplicationEmailToken t where t.tokenHash=:hash")
    Optional<RecruitmentApplicationEmailToken> findForUpdateByHash(@Param("hash") String hash);

    Optional<RecruitmentApplicationEmailToken> findFirstByTenantIdAndApplicationIdAndPurposeOrderByCreatedAtDesc(
            UUID tenantId, UUID applicationId, EmailTokenPurpose purpose);

    @Modifying
    @Query("update RecruitmentApplicationEmailToken t set t.revokedAt=:now where t.applicationId=:applicationId and t.consumedAt is null and t.revokedAt is null")
    int revokeActive(@Param("applicationId") UUID applicationId, @Param("now") LocalDateTime now);
}
