package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.model.RecruitmentEnums.ApplicationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationLifecycleTest {
    @Test
    void supportsEveryForwardTransitionDecisionAndReplay() {
        ApplicationStatus state=ApplicationStatus.SUBMITTED_UNVERIFIED;
        for(ApplicationStatus target:new ApplicationStatus[]{ApplicationStatus.SUBMITTED,ApplicationStatus.INTERVIEW_INVITED,
                ApplicationStatus.INTERVIEW_SCHEDULED,ApplicationStatus.INTERVIEW_COMPLETED,ApplicationStatus.UNDER_REVIEW}){
            state=ApplicationLifecycle.transition(state,target);
        }
        assertEquals(ApplicationStatus.SHORTLISTED,ApplicationLifecycle.transition(state,ApplicationStatus.SHORTLISTED));
        assertEquals(ApplicationStatus.REJECTED,ApplicationLifecycle.transition(state,ApplicationStatus.REJECTED));
        assertEquals(state,ApplicationLifecycle.transition(state,state));
    }

    @Test
    void allowsWithdrawalFromEachPreDecisionStateAndProtectsTerminals() {
        for(ApplicationStatus state:ApplicationStatus.values()){
            if(ApplicationLifecycle.isWithdrawable(state))assertEquals(ApplicationStatus.WITHDRAWN,ApplicationLifecycle.transition(state,ApplicationStatus.WITHDRAWN));
        }
        for(ApplicationStatus terminal:new ApplicationStatus[]{ApplicationStatus.SHORTLISTED,ApplicationStatus.REJECTED,ApplicationStatus.WITHDRAWN}){
            assertThrows(ConflictException.class,()->ApplicationLifecycle.transition(terminal,ApplicationStatus.SUBMITTED));
        }
        assertThrows(ConflictException.class,()->ApplicationLifecycle.transition(ApplicationStatus.SUBMITTED,ApplicationStatus.UNDER_REVIEW));
    }
}
