package com.cacanode.api.bootstrap.diagnostics;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformDiagnosticsPropertiesTest {
    @Test
    void validatesDurationsParallelismAndQueueThresholdOrdering() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            assertThat(validator.validate(properties(Duration.ofSeconds(2), 12, 25, 100))).isEmpty();
            assertThat(validator.validate(properties(Duration.ZERO, 12, 25, 100))).isNotEmpty();
            assertThat(validator.validate(properties(Duration.ofSeconds(2), 0, 25, 100))).isNotEmpty();
            assertThat(validator.validate(properties(Duration.ofSeconds(2), 12, 100, 100))).isNotEmpty();
        }
    }

    private PlatformDiagnosticsProperties properties(Duration timeout, int parallelism, long warning, long critical) {
        return new PlatformDiagnosticsProperties(timeout, Duration.ofSeconds(3), Duration.ofSeconds(10), parallelism,
                warning, critical, "", "", "", "", "", "", false, "");
    }
}
