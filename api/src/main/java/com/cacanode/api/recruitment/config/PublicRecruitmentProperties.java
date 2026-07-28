package com.cacanode.api.recruitment.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.recruitment.public")
public record PublicRecruitmentProperties(
        @NotBlank String tokenPepper,
        @NotBlank String cursorEncryptionKey,
        @NotBlank String candidateBaseUrl,
        boolean cookieSecure,
        boolean turnstileEnabled,
        String turnstileSiteKey,
        String turnstileSecretKey,
        boolean scannerEnabled,
        @NotBlank String clamavHost,
        @Min(1) @Max(65535) int clamavPort,
        @Min(1) @Max(5242880) long maxCvBytes) {

    public PublicRecruitmentProperties {
        tokenPepper = defaulted(tokenPepper, "development-recruitment-token-pepper");
        cursorEncryptionKey = defaulted(cursorEncryptionKey, "development-recruitment-cursor-key");
        candidateBaseUrl = defaulted(candidateBaseUrl, "http://localhost:3000/applications/manage");
        clamavHost = defaulted(clamavHost, "localhost");
        if (clamavPort == 0) clamavPort = 3310;
        if (maxCvBytes == 0) maxCvBytes = 5L * 1024 * 1024;
    }

    @AssertTrue(message = "Turnstile requires both site and secret keys")
    public boolean isTurnstileConfigurationValid() {
        return !turnstileEnabled || (hasText(turnstileSiteKey) && hasText(turnstileSecretKey));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    private static String defaulted(String value,String fallback){return hasText(value)?value:fallback;}
}
