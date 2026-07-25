package com.cacanode.api.recruitment.repository;

import com.cacanode.api.recruitment.model.RecruitmentCvAnalysisInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecruitmentCvAnalysisInboxRepository extends JpaRepository<RecruitmentCvAnalysisInbox,UUID> {
    Optional<RecruitmentCvAnalysisInbox> findByTenantIdAndAnalysisId(UUID tenantId,UUID analysisId);
}
