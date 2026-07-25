package com.cacanode.api.recruitment.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix="app.recruitment.cv-analysis")
public record CvAnalysisProperties(
        @NotBlank String policyVersion,
        @NotBlank String modelVersion,
        @Min(1) @Max(50000) int maxExtractedCharacters,
        @Min(1) @Max(250) int maxEvidenceSegments,
        @Min(2) @Max(2) int maxPersonalizedQuestions,
        @Min(3) @Max(3) int maxProcessingAttempts,
        @Min(1) @Max(10) int maxPublicationAttempts,
        @Min(100) long publisherDelayMs,
        @Min(1) long initialBackoffSeconds,
        @Min(1) @Max(3600) long maxBackoffSeconds) {
}
