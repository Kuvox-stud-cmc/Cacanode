package com.cacanode.api.bootstrap.diagnostics;

import com.cacanode.api.platform.api.PlatformDiagnosticsApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformQueueStatusTest {
    @Test
    void appliesBoundaryDepthsConsumerAndDlqPrecedence() {
        assertDecision(false, 24, 1, PlatformDiagnosticsApi.Status.UP, null);
        assertDecision(false, 25, 1, PlatformDiagnosticsApi.Status.DEGRADED, PlatformDiagnosticsApi.ErrorCode.QUEUE_WARNING_DEPTH);
        assertDecision(false, 99, 1, PlatformDiagnosticsApi.Status.DEGRADED, PlatformDiagnosticsApi.ErrorCode.QUEUE_WARNING_DEPTH);
        assertDecision(false, 100, 1, PlatformDiagnosticsApi.Status.DOWN, PlatformDiagnosticsApi.ErrorCode.QUEUE_CRITICAL_DEPTH);
        assertDecision(false, 24, 0, PlatformDiagnosticsApi.Status.DEGRADED, PlatformDiagnosticsApi.ErrorCode.CONSUMERS_ABSENT);
        assertDecision(false, 100, 0, PlatformDiagnosticsApi.Status.DOWN, PlatformDiagnosticsApi.ErrorCode.QUEUE_CRITICAL_DEPTH);
        assertDecision(true, 1, 0, PlatformDiagnosticsApi.Status.DOWN, PlatformDiagnosticsApi.ErrorCode.DLQ_NOT_EMPTY);
    }

    @Test
    void unreadableQueuesRemainUnknownBeforeDepthRules() {
        var decision = PlatformDiagnosticsAdapter.deriveQueueStatus(false, 1000, 0, 25, 100,
                PlatformDiagnosticsApi.ErrorCode.QUEUE_MISSING);
        assertThat(decision.status()).isEqualTo(PlatformDiagnosticsApi.Status.UNKNOWN);
        assertThat(decision.errorCode()).isEqualTo(PlatformDiagnosticsApi.ErrorCode.QUEUE_MISSING);
    }

    private void assertDecision(boolean dlq, long ready, int consumers, PlatformDiagnosticsApi.Status status,
                                PlatformDiagnosticsApi.ErrorCode error) {
        var decision = PlatformDiagnosticsAdapter.deriveQueueStatus(dlq, ready, consumers, 25, 100, null);
        assertThat(decision.status()).isEqualTo(status);
        assertThat(decision.errorCode()).isEqualTo(error);
    }
}
