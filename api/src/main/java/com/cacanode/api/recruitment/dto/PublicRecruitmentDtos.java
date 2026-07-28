package com.cacanode.api.recruitment.dto;

import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PublicRecruitmentDtos {
    private PublicRecruitmentDtos() {}

    public record PublicJob(
            UUID publicId, String tenantSlug, String companyName, String title, String description,
            String descriptionHtml,
            String department, String location, EmploymentType employmentType, WorkMode workMode,
            ExperienceLevel experienceLevel, String language, CvPolicy cvPolicy,
            CvAiMode cvAiMode, boolean cvAiDisclosed, List<PublicScreeningQuestion> screeningQuestions,
            LocalDateTime publishedAt, LocalDateTime closingAt, boolean discoverable) {
        public PublicJob(UUID publicId,String tenantSlug,String companyName,String title,String description,
                String department,String location,EmploymentType employmentType,WorkMode workMode,
                ExperienceLevel experienceLevel,String language,CvPolicy cvPolicy,CvAiMode cvAiMode,boolean cvAiDisclosed,
                LocalDateTime publishedAt,LocalDateTime closingAt){this(publicId,tenantSlug,companyName,title,description,
                null,department,location,employmentType,workMode,experienceLevel,language,cvPolicy,cvAiMode,cvAiDisclosed,List.of(),
                publishedAt,closingAt,true);}
    }

    public record PublicScreeningOption(UUID optionId,String label) {}
    public record PublicScreeningQuestion(UUID questionId,String prompt,List<PublicScreeningOption> options) {}
    public record ScreeningAnswer(@NotNull UUID questionId,@NotNull UUID optionId) {}

    public record PublicJobPage(List<PublicJob> items, String nextCursor) {}

    public record ApplicationData(
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phone,
            @NotBlank @Pattern(regexp = "vi-VN|en-US") String locale,
            @AssertTrue boolean privacyConsent,
            boolean cvUseConsent,
            @NotNull List<@Valid ScreeningAnswer> screeningAnswers) {
        public ApplicationData(String fullName,String email,String phone,String locale,boolean privacyConsent,
                boolean cvUseConsent){this(fullName,email,phone,locale,privacyConsent,cvUseConsent,List.of());}
    }

    public record AcceptedApplication(boolean accepted, String message) {
        public static AcceptedApplication generic() {
            return new AcceptedApplication(true,
                    "If the application can be accepted, a secure access link will be sent by email.");
        }
    }

    public record TokenExchange(@NotBlank String token) {}
    public record InvitationSessionResponse(String csrfToken,InvitationDetails invitation) {}
    public record CandidateSessionResponse(String csrfToken, CandidateApplication application) {}
    public record CandidateApplication(UUID applicationId, UUID jobPublicId, String companyName,
            String jobTitle, ApplicationStatus status, LocalDateTime submittedAt,
            LocalDateTime verifiedAt, LocalDateTime withdrawnAt, boolean cvPresent) {}

    public record InvitationDetails(UUID interviewId,String companyName,String jobTitle,String candidateName,
            InterviewStatus status,Instant scheduledStartAt,Instant scheduledEndAt,String schedulingTimezone,
            LocalDateTime invitationExpiresAt,int rescheduleCount) {}
    public record InterviewSlot(Instant startAt,Instant endAt,String schedulingTimezone) {}
    public record SlotPage(List<InterviewSlot> items,LocalDate nextFrom,String schedulingTimezone) {}
    public record ScheduleRequest(@NotNull Instant startAt) {}
}
