package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus;

import java.util.Map;
import java.util.Set;

public final class InterviewLifecycle {
    private static final Set<InterviewStatus> TERMINAL = Set.of(InterviewStatus.COMPLETED,
            InterviewStatus.NO_ANSWER, InterviewStatus.DECLINED, InterviewStatus.FAILED,
            InterviewStatus.CANCELLED, InterviewStatus.EXPIRED);
    private static final Map<InterviewStatus, InterviewStatus> FORWARD = Map.of(
            InterviewStatus.INVITED, InterviewStatus.SCHEDULED,
            InterviewStatus.SCHEDULED, InterviewStatus.PREPARING,
            InterviewStatus.PREPARING, InterviewStatus.CALLING,
            InterviewStatus.CALLING, InterviewStatus.RINGING,
            InterviewStatus.RINGING, InterviewStatus.CONSENT_PENDING,
            InterviewStatus.CONSENT_PENDING, InterviewStatus.IN_PROGRESS,
            InterviewStatus.IN_PROGRESS, InterviewStatus.COMPLETED);

    private InterviewLifecycle() {}

    public static InterviewStatus transition(InterviewStatus current, InterviewStatus target) {
        if (current == target) return current;
        if (TERMINAL.contains(current)) throw new ConflictException("A terminal interview cannot be reopened");
        if (FORWARD.get(current) == target || mayTerminate(current, target)) return target;
        throw new ConflictException("Invalid interview transition from " + current + " to " + target);
    }

    public static boolean isTerminal(InterviewStatus status) { return TERMINAL.contains(status); }

    private static boolean mayTerminate(InterviewStatus current, InterviewStatus target) {
        return switch (target) {
            case CANCELLED, EXPIRED -> current != InterviewStatus.IN_PROGRESS;
            case FAILED -> current == InterviewStatus.PREPARING || current == InterviewStatus.CALLING
                    || current == InterviewStatus.RINGING || current == InterviewStatus.CONSENT_PENDING
                    || current == InterviewStatus.IN_PROGRESS;
            case NO_ANSWER -> current == InterviewStatus.CALLING || current == InterviewStatus.RINGING;
            case DECLINED -> current == InterviewStatus.RINGING || current == InterviewStatus.CONSENT_PENDING;
            default -> false;
        };
    }
}
