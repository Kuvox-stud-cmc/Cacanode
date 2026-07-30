package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.event.durable.DurableEventPublisher;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.recruitment.api.event.CandidateCompletionEmailRequestedEvent;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.config.RecruitmentProperties;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.model.RecruitmentApplicationEmailToken;
import com.cacanode.api.recruitment.model.RecruitmentEnums.ApplicationStatus;
import com.cacanode.api.recruitment.model.RecruitmentEnums.EmailTokenPurpose;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationEmailTokenRepository;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import com.cacanode.api.recruitment.repository.RecruitmentCandidateRepository;
import com.cacanode.api.recruitment.repository.RecruitmentJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.recruitment", name = "enabled", havingValue = "true")
public class RecruitmentApplicationCompletionLinkService {
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentCandidateRepository candidates;
    private final RecruitmentJobRepository jobs;
    private final RecruitmentApplicationEmailTokenRepository emailTokens;
    private final RecruitmentTokenSupport tokens;
    private final PublicRecruitmentProperties properties;
    private final RecruitmentProperties recruitmentProperties;
    private final DurableEventPublisher events;
    private final Clock clock;

    @Transactional
    public RecruitmentDtos.CompletionLinkResponse send(UUID tenantId, UUID applicationId) {
        if (!recruitmentProperties.publicJobsEnabled()) {
            throw new ConflictException("CANDIDATE_COMPLETION_DISABLED");
        }
        var application = applications.findForUpdate(tenantId, applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application was not found"));
        if (application.getStatus() != ApplicationStatus.AWAITING_CANDIDATE) {
            throw new ConflictException("APPLICATION_COMPLETION_NOT_PENDING");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        emailTokens.findFirstByTenantIdAndApplicationIdAndPurposeOrderByCreatedAtDesc(
                        tenantId, applicationId, EmailTokenPurpose.COMPLETION)
                .filter(token -> token.getCreatedAt().plusSeconds(60).isAfter(now))
                .ifPresent(token -> { throw new ConflictException("COMPLETION_LINK_COOLDOWN"); });

        var candidate = candidates.findByIdAndTenantId(application.getCandidateId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate was not found"));
        var job = jobs.findByIdAndTenantId(application.getJobId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Job was not found"));
        emailTokens.revokeActive(applicationId, now);
        String raw = tokens.opaqueToken();
        var token = new RecruitmentApplicationEmailToken();
        token.setTenantId(tenantId);
        token.setApplicationId(applicationId);
        token.setJobId(application.getJobId());
        token.setPurpose(EmailTokenPurpose.COMPLETION);
        token.setTokenHash(tokens.hash(raw));
        token.setExpiresAt(now.plusDays(7));
        emailTokens.saveAndFlush(token);

        events.publish("recruitment.candidate-completion-email.requested.v1", 1,
                new CandidateCompletionEmailRequestedEvent(
                        tenantId, applicationId, candidate.getEmail(), candidate.getFullName(),
                        job.getFrozenCompanyName(), job.getTitle(), application.getLocale(),
                        RecruitmentCandidateLinks.withToken(properties.candidateBaseUrl(), "token", raw)));
        return new RecruitmentDtos.CompletionLinkResponse(true, "COMPLETION_LINK_SENT");
    }
}
