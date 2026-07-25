package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterviewLifecycleTest {
    @Test
    void permitsForwardPathAndIdempotentReplay() {
        InterviewStatus state = InterviewStatus.INVITED;
        for (InterviewStatus target : new InterviewStatus[]{InterviewStatus.SCHEDULED, InterviewStatus.PREPARING,
                InterviewStatus.CALLING, InterviewStatus.RINGING, InterviewStatus.CONSENT_PENDING,
                InterviewStatus.IN_PROGRESS, InterviewStatus.COMPLETED}) {
            state = InterviewLifecycle.transition(state, target);
        }
        assertEquals(InterviewStatus.COMPLETED, state);
        assertEquals(state, InterviewLifecycle.transition(state, state));
    }

    @Test
    void terminalStatesCannotReopenAndInvalidSkipsConflict() {
        assertThrows(ConflictException.class, () -> InterviewLifecycle.transition(InterviewStatus.INVITED, InterviewStatus.IN_PROGRESS));
        assertThrows(ConflictException.class, () -> InterviewLifecycle.transition(InterviewStatus.NO_ANSWER, InterviewStatus.SCHEDULED));
        assertEquals(InterviewStatus.NO_ANSWER, InterviewLifecycle.transition(InterviewStatus.RINGING, InterviewStatus.NO_ANSWER));
        assertEquals(InterviewStatus.DECLINED, InterviewLifecycle.transition(InterviewStatus.CONSENT_PENDING, InterviewStatus.DECLINED));
    }
}
