package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.model.RecruitmentEnums.ApplicationStatus;

import java.util.Set;

public final class ApplicationLifecycle {
    private static final Set<ApplicationStatus> WITHDRAWABLE = Set.of(
            ApplicationStatus.AWAITING_CANDIDATE, ApplicationStatus.SUBMITTED_UNVERIFIED, ApplicationStatus.SUBMITTED,
            ApplicationStatus.INTERVIEW_INVITED, ApplicationStatus.INTERVIEW_SCHEDULED,
            ApplicationStatus.INTERVIEW_COMPLETED, ApplicationStatus.UNDER_REVIEW);

    private ApplicationLifecycle() {}

    public static ApplicationStatus transition(ApplicationStatus current, ApplicationStatus target) {
        if (current == target) return current;
        if (target == ApplicationStatus.WITHDRAWN && WITHDRAWABLE.contains(current)) return target;
        ApplicationStatus expected = switch (current) {
            case AWAITING_CANDIDATE -> null;
            case SUBMITTED_UNVERIFIED -> ApplicationStatus.SUBMITTED;
            case SUBMITTED -> ApplicationStatus.INTERVIEW_INVITED;
            case INTERVIEW_INVITED -> ApplicationStatus.INTERVIEW_SCHEDULED;
            case INTERVIEW_SCHEDULED -> ApplicationStatus.INTERVIEW_COMPLETED;
            case INTERVIEW_COMPLETED -> ApplicationStatus.UNDER_REVIEW;
            default -> null;
        };
        if (expected == target) return target;
        if (current == ApplicationStatus.UNDER_REVIEW
                && (target == ApplicationStatus.SHORTLISTED || target == ApplicationStatus.REJECTED)) return target;
        throw new ConflictException("Invalid application transition from " + current + " to " + target);
    }

    public static boolean isWithdrawable(ApplicationStatus status) { return WITHDRAWABLE.contains(status); }
}
