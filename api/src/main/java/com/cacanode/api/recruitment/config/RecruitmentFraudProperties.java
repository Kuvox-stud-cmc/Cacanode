package com.cacanode.api.recruitment.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix="app.recruitment.calling.fraud")
public record RecruitmentFraudProperties(@Min(1) int destinationAttemptsPerDay,
        @Min(1) int tenantAttemptsPerDay,@Min(1) int destinationTenantLimit,String fingerprintSecret) {}
