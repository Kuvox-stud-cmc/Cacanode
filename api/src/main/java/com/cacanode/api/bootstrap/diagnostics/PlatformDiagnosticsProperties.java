package com.cacanode.api.bootstrap.diagnostics;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.platform-administration.diagnostics")
public record PlatformDiagnosticsProperties(
        @DefaultValue("2s") Duration probeTimeout,
        @DefaultValue("3s") Duration refreshBudget,
        @DefaultValue("10s") Duration cacheTtl,
        @DefaultValue("12") @Min(1) @Max(64) int parallelism,
        @DefaultValue("25") @Min(0) long warningDepth,
        @DefaultValue("100") @Min(1) long criticalDepth,
        String aiApiUrl,
        String graphServiceUrl,
        String qdrantUrl,
        String qdrantApiKey,
        String ollamaUrl,
        String rerankerUrl,
        boolean rerankerEnabled,
        String publicEdgeUrl) {

    @AssertTrue(message = "Diagnostics durations must be positive")
    public boolean isDurationsValid() {
        return probeTimeout != null && !probeTimeout.isZero() && !probeTimeout.isNegative()
                && refreshBudget != null && !refreshBudget.isZero() && !refreshBudget.isNegative()
                && cacheTtl != null && !cacheTtl.isZero() && !cacheTtl.isNegative();
    }

    @AssertTrue(message = "Diagnostics queue depths require 0 <= warning < critical")
    public boolean isQueueDepthsValid() {
        return warningDepth >= 0 && warningDepth < criticalDepth;
    }
}
