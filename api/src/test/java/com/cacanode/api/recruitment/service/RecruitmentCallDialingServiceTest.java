package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.model.RecruitmentEnums.InterviewStatus;
import com.cacanode.api.recruitment.model.RecruitmentInterview;
import com.cacanode.api.recruitment.model.RecruitmentTenantSettings;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentCallDialingServiceTest {
    @Test
    void onlyManualDevelopmentCallsBypassTheScheduledDialWindow() {
        assertTrue(RecruitmentCallDialingService.bypassDialWindow(true,"development"));
        assertTrue(RecruitmentCallDialingService.bypassDialWindow(true,"DEVELOPMENT"));
        assertFalse(RecruitmentCallDialingService.bypassDialWindow(false,"development"));
        assertFalse(RecruitmentCallDialingService.bypassDialWindow(true,"production"));
        assertFalse(RecruitmentCallDialingService.bypassDialWindow(true,"staging"));
    }

    @Test
    void onlyManualDevelopmentCallsBypassDailyAttemptCounters() {
        assertTrue(RecruitmentCallDialingService.bypassDailyAttemptLimits(true,"development"));
        assertFalse(RecruitmentCallDialingService.bypassDailyAttemptLimits(false,"development"));
        assertFalse(RecruitmentCallDialingService.bypassDailyAttemptLimits(true,"staging"));
        assertFalse(RecruitmentCallDialingService.bypassDailyAttemptLimits(true,"production"));
    }

    @Test
    void everyTerminalSeedInterviewCanUseDevelopmentRedial() {
        RecruitmentInterview interview=new RecruitmentInterview();
        interview.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        for(InterviewStatus status:new InterviewStatus[]{InterviewStatus.COMPLETED,InterviewStatus.FAILED,
                InterviewStatus.DECLINED,InterviewStatus.NO_ANSWER,InterviewStatus.CANCELLED,
                InterviewStatus.EXPIRED}) {
            interview.setStatus(status);
            assertTrue(RecruitmentCallDialingService.developmentRedial(
                    interview,true,"development"),status.name());
        }
        assertFalse(RecruitmentCallDialingService.developmentRedial(
                interview,false,"development"));
        assertFalse(RecruitmentCallDialingService.developmentRedial(
                interview,true,"production"));

        interview.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        assertFalse(RecruitmentCallDialingService.developmentRedial(
                interview,true,"development"));

        interview.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        interview.setStatus(InterviewStatus.SCHEDULED);
        assertFalse(RecruitmentCallDialingService.developmentRedial(
                interview,true,"development"));
        interview.setStatus(InterviewStatus.IN_PROGRESS);
        assertFalse(RecruitmentCallDialingService.developmentRedial(
                interview,true,"development"));
    }

    @Test
    void seededDevelopmentRedialAdoptsCurrentRecordingSetting() {
        RecruitmentInterview interview=new RecruitmentInterview();
        interview.setRecordingEnabled(false);interview.setRecordingRetentionDays(0);
        RecruitmentTenantSettings settings=new RecruitmentTenantSettings();
        settings.setRecordingEnabled(true);settings.setRecordingRetentionDays(7);

        RecruitmentCallDialingService.applyDevelopmentRecordingSnapshot(
                interview,settings,true);

        assertTrue(interview.isRecordingEnabled());
        assertEquals(7,interview.getRecordingRetentionDays());
    }

    @Test
    void disabledRecordingCapabilityClearsTheDevelopmentRedialSnapshot() {
        RecruitmentInterview interview=new RecruitmentInterview();
        interview.setRecordingEnabled(true);interview.setRecordingRetentionDays(30);
        RecruitmentTenantSettings settings=new RecruitmentTenantSettings();
        settings.setRecordingEnabled(true);settings.setRecordingRetentionDays(7);

        RecruitmentCallDialingService.applyDevelopmentRecordingSnapshot(
                interview,settings,false);

        assertFalse(interview.isRecordingEnabled());
        assertEquals(0,interview.getRecordingRetentionDays());
    }
}
