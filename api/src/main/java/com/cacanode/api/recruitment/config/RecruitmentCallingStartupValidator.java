package com.cacanode.api.recruitment.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="calling-enabled",havingValue="true")
public class RecruitmentCallingStartupValidator {
    private final RecruitmentCallingProperties properties;
    private final RecruitmentProperties recruitment;

    @PostConstruct
    void validate() {
        if(!recruitment.enabled()||!recruitment.messagingEnabled())
            throw new IllegalStateException("Recruitment calling requires recruitment and messaging");
        if(!properties.hasCompleteCredentialSet())
            throw new IllegalStateException("Calling requires complete Twilio, Cartesia, and runtime-token credentials");
        if(!properties.hasSecurePublicUrls())
            throw new IllegalStateException("Calling requires public HTTPS callback and WSS media URLs");
        if(!properties.hasValidFromNumber())throw new IllegalStateException("Twilio source number must use E.164");
        if(properties.transportSmokeMode()&&"production".equalsIgnoreCase(properties.appEnvironment()))
            throw new IllegalStateException("Interview transport smoke mode is forbidden in production");
        if(properties.interviewEngineEnabled()==properties.transportSmokeMode())
            throw new IllegalStateException("Calling requires exactly one of interview engine or transport smoke mode");
        if("production".equalsIgnoreCase(properties.appEnvironment())&&
                !(properties.interviewEngineEnabled()&&properties.durableResultsEnabled()))
            throw new IllegalStateException("Production calling requires the interview engine and durable results");
    }
}
