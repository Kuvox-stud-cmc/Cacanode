package com.cacanode.api.recruitment.query;

import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationEmailTokenRepository;
import com.cacanode.api.recruitment.repository.RecruitmentApplicationRepository;
import com.cacanode.api.recruitment.repository.RecruitmentCandidateSessionRepository;
import com.cacanode.api.recruitment.service.ApplicationSubmissionTransitionService;
import com.cacanode.api.recruitment.service.RecruitmentInterviewCancellationService;
import com.cacanode.api.recruitment.service.RecruitmentTokenSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CandidateAccessServiceTimeZoneTest {
    @Test void candidateSessionTimestampsUseTheRuntimeZoneUsedByHibernateCreationTimestamps() {
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            Clock clock=Clock.fixed(Instant.parse("2026-07-26T11:37:00Z"),ZoneOffset.UTC);
            var service=new CandidateAccessService(
                    mock(RecruitmentApplicationEmailTokenRepository.class),
                    mock(RecruitmentCandidateSessionRepository.class),
                    mock(RecruitmentApplicationRepository.class),
                    mock(ApplicationSubmissionTransitionService.class),
                    mock(RecruitmentCvStorageService.class),
                    mock(RecruitmentInterviewCancellationService.class),
                    mock(RecruitmentTokenSupport.class),
                    new PublicRecruitmentProperties(null,null,null,false,false,null,null,false,null,0,0),
                    mock(NamedParameterJdbcTemplate.class),clock);

            assertEquals(LocalDateTime.of(2026,7,26,18,37),service.sessionNow());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
