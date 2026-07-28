package com.cacanode.api.common.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.platform-administration")
public record OperationalFailureProperties(@DefaultValue("10m") Duration failureStalledAfter) {
    @AssertTrue(message = "Platform failure stalled duration must be positive")
    public boolean isFailureStalledAfterValid() {
        return failureStalledAfter != null && !failureStalledAfter.isZero() && !failureStalledAfter.isNegative();
    }
}
