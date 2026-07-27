package com.cacanode.api.recruitment.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix="app.recruitment.calling")
public record RecruitmentCallingProperties(
        boolean enabled,
        boolean schedulerEnabled,
        @Min(0) int claimEarlySeconds,
        @Min(0) int claimLateSeconds,
        @Min(1) @Max(3) int preparationMaxAttempts,
        @Min(1) @Max(3) int cancellationMaxAttempts,
        @Min(1) int globalConcurrency,
        @Min(1) int tenantConcurrency,
        String twilioAccountSid,
        String twilioApiKeySid,
        String twilioApiKeySecret,
        String twilioAuthToken,
        String twilioFromNumber,
        String callbackBaseUrl,
        String mediaStreamWssUrl,
        String consentVoiceEnglish,
        String consentVoiceVietnamese,
        String cartesiaApiKey,
        String cartesiaEnglishVoiceId,
        String cartesiaVietnameseVoiceId,
        String runtimeTokenSecret,
        boolean transportSmokeMode,
        boolean interviewEngineEnabled,
        boolean durableResultsEnabled,
        String appEnvironment,
        boolean callForMoreThan600) {

    public RecruitmentCallingProperties {
        consentVoiceEnglish=defaulted(consentVoiceEnglish,"alice");
        consentVoiceVietnamese=defaulted(consentVoiceVietnamese,"Google.vi-VN-Standard-A");
        appEnvironment=defaulted(appEnvironment,"development");
    }

    @AssertTrue(message="Calling requires complete Twilio, Cartesia, and runtime-token credentials")
    public boolean isCredentialSetValid() {
        return !enabled||all(twilioAccountSid,twilioApiKeySid,twilioApiKeySecret,twilioAuthToken,
                twilioFromNumber,callbackBaseUrl,mediaStreamWssUrl,cartesiaApiKey,
                cartesiaEnglishVoiceId,cartesiaVietnameseVoiceId,runtimeTokenSecret);
    }

    @AssertTrue(message="Calling requires public HTTPS callback and WSS media URLs")
    public boolean isPublicUrlValid() {
        return !enabled||(callbackBaseUrl.startsWith("https://")&&mediaStreamWssUrl.startsWith("wss://"));
    }

    @AssertTrue(message="Twilio calling requires an E.164 source number")
    public boolean isFromNumberValid() {
        return !enabled||(twilioFromNumber!=null&&twilioFromNumber.matches("^\\+[1-9][0-9]{7,14}$"));
    }

    @AssertTrue(message="Interview transport smoke mode is forbidden in production")
    public boolean isSmokeEnvironmentValid() {
        return !transportSmokeMode||!"production".equalsIgnoreCase(appEnvironment);
    }

    @AssertTrue(message="Calling requires exactly one of interview engine or transport smoke mode")
    public boolean isRuntimeModeValid() {
        return !enabled||(interviewEngineEnabled!=transportSmokeMode);
    }

    @AssertTrue(message="Production calling requires the interview engine")
    public boolean isProductionEngineValid() {
        return !enabled||!"production".equalsIgnoreCase(appEnvironment)
                ||(interviewEngineEnabled&&durableResultsEnabled);
    }

    @AssertTrue(message="Tenant concurrency cannot exceed global concurrency")
    public boolean isConcurrencyValid() {return tenantConcurrency<=globalConcurrency;}

    public boolean hasCompleteCredentialSet(){return all(twilioAccountSid,twilioApiKeySid,twilioApiKeySecret,
            twilioAuthToken,twilioFromNumber,callbackBaseUrl,mediaStreamWssUrl,cartesiaApiKey,
            cartesiaEnglishVoiceId,cartesiaVietnameseVoiceId,runtimeTokenSecret);}
    public boolean hasSecurePublicUrls(){return callbackBaseUrl!=null&&callbackBaseUrl.startsWith("https://")
            &&mediaStreamWssUrl!=null&&mediaStreamWssUrl.startsWith("wss://");}
    public boolean hasValidFromNumber(){return twilioFromNumber!=null&&twilioFromNumber.matches("^\\+[1-9][0-9]{7,14}$");}

    private static boolean all(String... values){for(String value:values)if(value==null||value.isBlank())return false;return true;}
    private static String defaulted(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
}
