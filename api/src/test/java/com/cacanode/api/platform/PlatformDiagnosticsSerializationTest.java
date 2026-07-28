package com.cacanode.api.platform;

import com.cacanode.api.platform.api.PlatformDiagnosticsApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformDiagnosticsSerializationTest {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)(url|host|port|credential|secret|environment|docker|payload|exception|stack.?trace|raw.?message)");

    @Test
    void healthAndQueueContractsContainOnlyControlledFields() throws Exception {
        Instant checked = Instant.parse("2026-07-28T00:00:00Z");
        var component = new PlatformDiagnosticsApi.ComponentResult(PlatformDiagnosticsApi.Component.POSTGRESQL,
                PlatformDiagnosticsApi.Status.DOWN, 12L, checked, PlatformDiagnosticsApi.ErrorCode.CONNECTION_FAILURE);
        var runtime = new PlatformDiagnosticsApi.RuntimeMetrics(PlatformDiagnosticsApi.ResourceScope.APPLICATION_CONTAINER,
                PlatformDiagnosticsApi.CpuScope.JVM_PROCESS, 2.5, 4, 1, 2, 3, 4, 5, 6);
        var health = new PlatformDiagnosticsApi.HealthSnapshot(checked, PlatformDiagnosticsApi.Status.DOWN,
                List.of(component), runtime);
        var queue = new PlatformDiagnosticsApi.QueueResult(PlatformDiagnosticsApi.QueueId.DOCUMENT_INGESTION,
                PlatformDiagnosticsApi.QueueDomain.DOCUMENT, false, 25, 0, PlatformDiagnosticsApi.Status.DEGRADED,
                checked, PlatformDiagnosticsApi.ErrorCode.CONSUMERS_ABSENT);
        var page = new PlatformDiagnosticsApi.QueuePage(List.of(queue), 0, 20, 1, checked,
                PlatformDiagnosticsApi.Status.DEGRADED, 25, 100);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        assertThat(FORBIDDEN.matcher(mapper.writeValueAsString(health)).find()).isFalse();
        assertThat(FORBIDDEN.matcher(mapper.writeValueAsString(page)).find()).isFalse();
    }
}
