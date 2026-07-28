package com.cacanode.api.bootstrap.diagnostics;

import com.cacanode.api.platform.api.PlatformDiagnosticsApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformHttpProbeMappingTest {
    @Test
    void mapsReadinessAndPublicEdgeStatuses() {
        assertStatus(PlatformDiagnosticsAdapter.mapHttpStatus(204, false), PlatformDiagnosticsApi.Status.UP, null);
        assertStatus(PlatformDiagnosticsAdapter.mapHttpStatus(302, true), PlatformDiagnosticsApi.Status.UP, null);
        assertStatus(PlatformDiagnosticsAdapter.mapHttpStatus(302, false), PlatformDiagnosticsApi.Status.DOWN,
                PlatformDiagnosticsApi.ErrorCode.UNEXPECTED_RESPONSE);
        assertStatus(PlatformDiagnosticsAdapter.mapHttpStatus(503, false), PlatformDiagnosticsApi.Status.DEGRADED,
                PlatformDiagnosticsApi.ErrorCode.NOT_READY_RESPONSE);
        assertStatus(PlatformDiagnosticsAdapter.mapHttpStatus(401, false), PlatformDiagnosticsApi.Status.DOWN,
                PlatformDiagnosticsApi.ErrorCode.AUTHENTICATION_FAILURE);
        assertStatus(PlatformDiagnosticsAdapter.mapHttpStatus(500, false), PlatformDiagnosticsApi.Status.DOWN,
                PlatformDiagnosticsApi.ErrorCode.NOT_READY_RESPONSE);
    }

    private void assertStatus(PlatformDiagnosticsAdapter.ProbeResult result, PlatformDiagnosticsApi.Status status,
                              PlatformDiagnosticsApi.ErrorCode error) {
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.errorCode()).isEqualTo(error);
    }
}
