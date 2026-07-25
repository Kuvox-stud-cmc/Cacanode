package com.cacanode.api.recruitment.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.recruitment")
public record RecruitmentProperties(
        boolean enabled,
        boolean messagingEnabled,
        boolean publicJobsEnabled,
        boolean automationEnabled,
        boolean cvAiEnabled,
        boolean callingEnabled,
        boolean recordingEnabled) {

    @AssertTrue(message = "Recruitment child flags require RECRUITMENT_ENABLED")
    public boolean isMasterFlagValid() {
        return enabled || !(messagingEnabled || publicJobsEnabled || automationEnabled
                || cvAiEnabled || callingEnabled || recordingEnabled);
    }

    @AssertTrue(message = "Recruitment CV AI and calling require messaging")
    public boolean isMessagingDependencyValid() {
        return messagingEnabled || !(cvAiEnabled || callingEnabled);
    }

    @AssertTrue(message = "Recruitment automation requires public jobs")
    public boolean isAutomationDependencyValid() {
        return publicJobsEnabled || !automationEnabled;
    }

    @AssertTrue(message = "Recruitment recording requires calling")
    public boolean isRecordingDependencyValid() {
        return callingEnabled || !recordingEnabled;
    }

}
