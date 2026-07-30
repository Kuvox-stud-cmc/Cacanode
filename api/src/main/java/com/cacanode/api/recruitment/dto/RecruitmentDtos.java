package com.cacanode.api.recruitment.dto;

import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos.ScreeningAnswer;

public final class RecruitmentDtos {
    private RecruitmentDtos() {}

    public record SettingsResponse(AutomationMode defaultAutomationMode, CvAiMode cvAiMode,
            UUID defaultTemplateRevisionId, boolean recordingEnabled, int recordingRetentionDays,
            String schedulingTimezone, int slotGridMinutes, int minimumNoticeMinutes,
            int bookingHorizonDays, int invitationLifetimeDays, int rescheduleCutoffMinutes,
            List<Integer> reminderOffsetsMinutes, long version) {}
    public record SettingsUpdate(@NotNull AutomationMode defaultAutomationMode, @NotNull CvAiMode cvAiMode,
            UUID defaultTemplateRevisionId, boolean recordingEnabled, @Min(0) int recordingRetentionDays,
            @NotBlank String schedulingTimezone, @Min(0) int minimumNoticeMinutes,
            @Min(1) @Max(365) int bookingHorizonDays, @Min(1) @Max(30) int invitationLifetimeDays,
            @Min(0) int rescheduleCutoffMinutes,
            @Size(max=10) List<@Min(1) Integer> reminderOffsetsMinutes) {
        public SettingsUpdate(AutomationMode automation,CvAiMode cvAi,UUID revision,boolean recording,int retention){
            this(automation,cvAi,revision,recording,retention,"Asia/Ho_Chi_Minh",120,30,7,120,List.of(1440,60));
        }
    }

    public record ScreeningOption(@NotNull UUID optionId,@NotBlank @Size(max=300) String label) {}
    public record ScreeningQuestion(@NotNull UUID questionId,@NotBlank @Size(max=500) String prompt,
            @Size(min=2,max=10) List<@Valid ScreeningOption> options,
            @NotEmpty List<@NotNull UUID> acceptedOptionIds) {}

    public record JobWrite(@NotBlank @Size(max=200) String title, String description,
            @Size(max=200000) String descriptionHtml,
            @Size(max=120) String department, @Size(max=160) String location,
            EmploymentType employmentType, WorkMode workMode, ExperienceLevel experienceLevel,
            @Pattern(regexp="vi-VN|en-US") String language, @NotNull CvPolicy cvPolicy,
            AutomationMode automationModeOverride, CvAiMode cvAiModeOverride,
            UUID templateRevisionId, LocalDateTime closingAt,
            @Size(max=10) List<@Valid ScreeningQuestion> screeningQuestions) {
        public JobWrite(String title,String description,String department,String location,EmploymentType employmentType,
                WorkMode workMode,ExperienceLevel experienceLevel,String language,CvPolicy cvPolicy,
                AutomationMode automationModeOverride,CvAiMode cvAiModeOverride,UUID templateRevisionId,
                LocalDateTime closingAt){this(title,description,null,department,location,employmentType,workMode,experienceLevel,
                language,cvPolicy,automationModeOverride,cvAiModeOverride,templateRevisionId,closingAt,List.of());}
    }
    public record JobResponse(UUID id, UUID publicId, String title, String description, String descriptionHtml, String department,
            String location, EmploymentType employmentType, WorkMode workMode, ExperienceLevel experienceLevel,
            String language, JobStatus status,
            CvPolicy cvPolicy, AutomationMode automationModeOverride, CvAiMode cvAiModeOverride,
            AutomationMode effectiveAutomationMode, CvAiMode effectiveCvAiMode,
            boolean recordingEnabled, int recordingRetentionDays,
            UUID templateRevisionId, LocalDateTime closingAt, LocalDateTime publishedAt,
            LocalDateTime pausedAt, LocalDateTime closedAt, LocalDateTime archivedAt,
            UUID activeJobReservationId, String companyName, String companySlug, long version,
            List<ScreeningQuestion> screeningQuestions, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public JobResponse(UUID id,UUID publicId,String title,String description,String descriptionHtml,String department,String location,
                EmploymentType employmentType,WorkMode workMode,ExperienceLevel experienceLevel,String language,
                JobStatus status,CvPolicy cvPolicy,AutomationMode automationModeOverride,CvAiMode cvAiModeOverride,
                AutomationMode effectiveAutomationMode,CvAiMode effectiveCvAiMode,boolean recordingEnabled,
                int recordingRetentionDays,UUID templateRevisionId,LocalDateTime closingAt,LocalDateTime publishedAt,
                LocalDateTime pausedAt,LocalDateTime closedAt,LocalDateTime archivedAt,UUID activeJobReservationId,
                String companyName,String companySlug,long version,LocalDateTime createdAt,LocalDateTime updatedAt){
            this(id,publicId,title,description,descriptionHtml,department,location,employmentType,workMode,experienceLevel,
                    language,status,cvPolicy,automationModeOverride,cvAiModeOverride,effectiveAutomationMode,effectiveCvAiMode,
                    recordingEnabled,recordingRetentionDays,templateRevisionId,closingAt,publishedAt,pausedAt,closedAt,
                    archivedAt,activeJobReservationId,companyName,companySlug,version,List.of(),createdAt,updatedAt);
        }
        public JobResponse(UUID id,UUID publicId,String title,String description,String department,String location,
                EmploymentType employmentType,WorkMode workMode,ExperienceLevel experienceLevel,String language,
                JobStatus status,CvPolicy cvPolicy,AutomationMode automationModeOverride,CvAiMode cvAiModeOverride,
                AutomationMode effectiveAutomationMode,CvAiMode effectiveCvAiMode,boolean recordingEnabled,
                int recordingRetentionDays,UUID templateRevisionId,LocalDateTime closingAt,LocalDateTime publishedAt,
                LocalDateTime pausedAt,LocalDateTime closedAt,LocalDateTime archivedAt,UUID activeJobReservationId,
                String companyName,String companySlug,long version,LocalDateTime createdAt,LocalDateTime updatedAt){
            this(id,publicId,title,description,null,department,location,employmentType,workMode,experienceLevel,language,status,
                    cvPolicy,automationModeOverride,cvAiModeOverride,effectiveAutomationMode,effectiveCvAiMode,
                    recordingEnabled,recordingRetentionDays,templateRevisionId,closingAt,publishedAt,pausedAt,closedAt,
                    archivedAt,activeJobReservationId,companyName,companySlug,version,List.of(),createdAt,updatedAt);
        }
    }

    public record JobPreview(UUID publicId, String tenantSlug, String companyName, String title,
            String description, String descriptionHtml, String department, String location,
            EmploymentType employmentType, WorkMode workMode, ExperienceLevel experienceLevel,
            String language, CvPolicy cvPolicy, JobStatus status, LocalDateTime publishedAt,
            LocalDateTime closingAt) {}

    public record InteractionLimits(@Min(0) int repetitionLimit, @Min(0) int clarificationLimit,
            @Min(1) int silenceTimeoutSeconds, @Min(0) int silencePromptLimit) {}
    public record Question(@NotNull UUID questionId, @Min(1) int position, @NotBlank String prompt,
            @NotBlank String competency, @NotBlank String rubric, @Min(0) int followUpLimit,
            @NotNull InterviewInferenceApi.QuestionSource source, String evidence) {}
    public record Section(@NotNull UUID sectionId, @Min(1) int position, @NotNull InterviewInferenceApi.SectionKind kind,
            @Pattern(regexp="vi-VN|en-US") String languageTag, @Min(1) int durationLimitSeconds,
            String transitionText, @NotEmpty List<@Valid Question> questions) {}
    public record RevisionContent(@NotBlank String introductionText, @NotBlank String disclosureText,
            @NotBlank String closingText, @Min(1) int durationLimitSeconds,
            @NotNull @Valid InteractionLimits interactionLimits, @NotEmpty List<@Valid Section> sections) {}
    public record TemplateCreate(@NotBlank @Size(max=160) String name, String description,
            @Pattern(regexp="vi-VN|en-US") String locale, @NotNull @Valid RevisionContent content) {}
    public record TemplatePatch(@Size(max=160) String name, String description) {}
    public record RevisionCreate(@NotNull @Valid RevisionContent content) {}
    public record TemplateResponse(UUID id, String name, String description, String locale, boolean archived,
            LocalDateTime archivedAt, int latestRevisionNumber, long version,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record RevisionResponse(UUID id, UUID templateId, int revisionNumber, RevisionContent content,
            String contentSha256, LocalDateTime createdAt) {}

    public record CandidateWrite(@NotBlank @Size(max=200) String fullName,
            @NotBlank @Email @Size(max=320) String email,
            @Pattern(regexp="^\\+[1-9][0-9]{7,14}$") String phone, String notes) {}
    public record CandidateResponse(UUID id, String fullName, String email, String phone, String notes,
            long version, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record TransitionRequest(@NotNull ApplicationStatus targetStatus) {}
    public record ApplicationCreate(@NotNull UUID jobId, @NotNull UUID candidateId) {}
    public record CompletionLinkResponse(boolean sent, String message) {}
    public record ApplicationResponse(UUID id, UUID jobId, String jobTitle, UUID candidateId,
            String candidateName, String candidateEmail, ApplicationStatus status, LocalDateTime submittedAt,
            LocalDateTime verifiedAt, LocalDateTime withdrawnAt, String locale,
            boolean cvPresent, CvAnalysisStatus cvAnalysisStatus, UUID templateRevisionId,
            String templateSnapshotSha256, String templateSnapshotVersion, BigDecimal overallScore,
            String englishBand, InterviewStatus interviewStatus, long version,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record ApplicationDetailResponse(ApplicationResponse application, CandidateResponse candidate,
            List<ScreeningQuestion> screeningQuestions, List<ScreeningAnswer> screeningAnswers) {}
    public record CvAnalysisEvidence(String anchorId,String excerpt,String sourceLocation) {}
    public record CvAnalysisSkill(String name,List<String> evidenceAnchorIds) {}
    public record CvAnalysisQuestion(UUID questionId,UUID targetSectionId,String prompt,String competency,
            String rubric,List<String> evidenceAnchorIds) {}
    public record CvAnalysisFitFinding(int weightPercent,int matchPercent,String evidenceStatus,
            String explanation,String jobExcerpt,String jobAnchorId,List<String> cvEvidenceAnchorIds) {}
    public record CvAnalysisRefreshRequest(@NotNull UUID requestId) {}
    public record CvAnalysisResponse(CvAiMode mode,CvAnalysisStatus status,String policyVersion,
            String modelVersion,LocalDateTime generatedAt,String summary,List<CvAnalysisEvidence> evidence,
            List<CvAnalysisSkill> skills,List<CvAnalysisQuestion> personalizedQuestions,String failureCode,
            boolean advisoryOnly,Integer fitScorePercent,String fitConfidence,String fitExplanation,
            List<CvAnalysisFitFinding> strengths,List<CvAnalysisFitFinding> gaps,Integer analysisRevision,
            boolean refreshAvailable,String refreshStatus,String refreshFailureCode) {}
    public record InterviewResponse(UUID id, UUID applicationId, UUID jobId, String jobTitle,
            UUID candidateId, String candidateName, InterviewStatus status, UUID templateRevisionId,
            String templateSnapshotSha256, String templateSnapshotVersion, LocalDateTime scheduledAt,
            Instant scheduledStartAt, Instant scheduledEndAt, String schedulingTimezone, int rescheduleCount,
            LocalDateTime startedAt, LocalDateTime completedAt, BigDecimal overallScore,
            String englishBand, boolean recordingEnabled, int recordingRetentionDays,
            long version, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record CallAttemptResponse(int attemptNumber, CallAttemptStatus status,
            LocalDateTime createdAt, LocalDateTime updatedAt, Instant answeredAt, Instant consentedAt,
            Instant terminalAt, String failureCode) {}
    public record DeliveryHistoryResponse(UUID id, String type, String status, String recipient,
            LocalDateTime sentAt, String failureReason, LocalDateTime createdAt) {}
    public record OverviewResponse(Map<String,Long> jobStatusCounts,
            Map<String,Long> applicationStatusCounts, Map<String,Long> interviewStatusCounts,
            List<InterviewResponse> upcomingInterviews) {
        public OverviewResponse {
            jobStatusCounts=Map.copyOf(jobStatusCounts);applicationStatusCounts=Map.copyOf(applicationStatusCounts);
            interviewStatusCounts=Map.copyOf(interviewStatusCounts);upcomingInterviews=List.copyOf(upcomingInterviews);
        }
    }
    public record DialResponse(UUID attemptId,CallAttemptStatus status,String failureCode,Instant acceptedAt) {}
    public record DialEligibilityResponse(boolean allowed,String reason,Instant windowOpensAt,
            Instant windowClosesAt,Instant serverTime) {}

    public record PageResult<T>(List<T> items, long totalCount) {}

    public record AvailabilityWindow(@Min(1) @Max(7) int dayOfWeek,@NotNull LocalTime startLocal,
            @NotNull LocalTime endLocal) {}
    public record AvailabilityException(@NotNull LocalDate date,@NotNull AvailabilityExceptionKind kind,
            @NotNull LocalTime startLocal,@NotNull LocalTime endLocal) {}
    public record AvailabilityResponse(String timezone,List<AvailabilityWindow> weeklyWindows,
            List<AvailabilityException> exceptions,long version) {}
    public record AvailabilityUpdate(@PositiveOrZero long version,
            @NotNull List<@Valid AvailabilityWindow> weeklyWindows,
            @NotNull List<@Valid AvailabilityException> exceptions) {}
}
