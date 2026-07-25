package com.cacanode.api.recruitment.model;

public final class RecruitmentEnums {
    private RecruitmentEnums() {}

    public enum AutomationMode { MANUAL, AUTO_INVITE_ALL, AUTO_INVITE_MATCHING }
    public enum CvAiMode { OFF, SUMMARY_ONLY, PERSONALIZED_QUESTIONS }
    public enum CvPolicy { DISABLED, OPTIONAL, REQUIRED }
    public enum EmploymentType { FULL_TIME, PART_TIME, CONTRACT, TEMPORARY, INTERNSHIP }
    public enum WorkMode { ONSITE, REMOTE, HYBRID }
    public enum ExperienceLevel { ENTRY, JUNIOR, MID, SENIOR, LEAD, EXECUTIVE }
    public enum EmailTokenPurpose { VERIFICATION, MANAGEMENT, DELETION_CONFIRMATION }
    public enum RolloutStage { OFF, INTERNAL, PILOT, GA }
    public enum PrivacyDeletionRequesterKind { CANDIDATE, TENANT_ADMIN }
    public enum PrivacyDeletionStatus {
        PENDING_CONFIRMATION, PENDING, PROCESSING, RETRY, COMPLETED, EXHAUSTED, CANCELLED
    }
    public enum CvStorageState { QUARANTINED, PROMOTED, DELETION_PENDING, DELETION_FAILED, DELETED }
    public enum JobStatus { DRAFT, PUBLISHED, PAUSED, CLOSED, ARCHIVED }
    public enum CvAnalysisStatus { NOT_REQUESTED, PENDING, COMPLETED, FAILED, SKIPPED_QUOTA, CANCELLED }
    public enum CvAnalysisRecordStatus { QUEUED, PUBLISHED, COMPLETED, FAILED, SKIPPED_QUOTA, CANCELLED }
    public enum ApplicationStatus {
        SUBMITTED_UNVERIFIED, SUBMITTED, INTERVIEW_INVITED, INTERVIEW_SCHEDULED,
        INTERVIEW_COMPLETED, UNDER_REVIEW, SHORTLISTED, REJECTED, WITHDRAWN
    }
    public enum InterviewStatus {
        INVITED, SCHEDULED, PREPARING, CALLING, RINGING, CONSENT_PENDING, IN_PROGRESS,
        COMPLETED, NO_ANSWER, DECLINED, FAILED, CANCELLED, EXPIRED
    }
    public enum CallAttemptStatus {
        PREPARING, READY, DIALING, CALLING, RINGING, CONSENT_PENDING, IN_PROGRESS,
        COMPLETED, NO_ANSWER, DECLINED, FAILED, CANCELLED, EXPIRED
    }
    public enum TwilioCallbackKind { VOICE, CONSENT, STATUS, STREAM_STATUS, RECORDING_STATUS, FALLBACK }
    public enum TwilioCallbackResult {
        APPLIED, DUPLICATE, IGNORED_OLDER, IGNORED_TERMINAL, REJECTED_CONFLICT, REJECTED_BINDING
    }
    public enum AutomationOutcome { PENDING, MANUAL, INVITED, NOT_MATCHED, INELIGIBLE }
    public enum AvailabilityExceptionKind { BLACKOUT, EXTRA }
    public enum CandidateEmailKind { INVITATION, CONFIRMATION, RESCHEDULE_CONFIRMATION, REMINDER }
    public enum CandidateEmailState { PENDING, DISPATCHING, SENT, FAILED, CANCELLED }
}
