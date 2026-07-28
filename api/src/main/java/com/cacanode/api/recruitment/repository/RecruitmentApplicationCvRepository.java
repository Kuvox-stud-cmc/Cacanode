package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentApplicationCv;
import com.cacanode.api.recruitment.model.RecruitmentEnums.CvStorageState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecruitmentApplicationCvRepository extends JpaRepository<RecruitmentApplicationCv, UUID> {
    Optional<RecruitmentApplicationCv> findByTenantIdAndApplicationIdAndActiveTrue(UUID tenantId, UUID applicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RecruitmentApplicationCv c where c.tenantId=:tenantId and c.applicationId=:applicationId and c.active=true")
    Optional<RecruitmentApplicationCv> findActiveForUpdate(@Param("tenantId") UUID tenantId, @Param("applicationId") UUID applicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RecruitmentApplicationCv c where c.active=true and c.storageState in :states and ((c.retainedUntil is not null and c.retainedUntil<=:now) or (c.deletionNextAttemptAt is not null and c.deletionNextAttemptAt<=:now)) order by coalesce(c.deletionNextAttemptAt,c.retainedUntil),c.id")
    List<RecruitmentApplicationCv> findCleanupBatch(@Param("states") Collection<CvStorageState> states, @Param("now") LocalDateTime now, org.springframework.data.domain.Pageable pageable);
}
