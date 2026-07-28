package com.cacanode.api.platform;

import com.cacanode.api.platform.api.PlatformDiagnosticsApi;
import com.cacanode.api.platform.controller.PlatformDiagnosticsController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformDiagnosticsControllerTest {
    private final PlatformDiagnosticsApi diagnostics = mock(PlatformDiagnosticsApi.class);
    private final PlatformDiagnosticsController controller = new PlatformDiagnosticsController(diagnostics);

    @Test
    void isPlatformFeatureFlagged() {
        ConditionalOnProperty condition = PlatformDiagnosticsController.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.prefix()).isEqualTo("app.platform-administration");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(PlatformDiagnosticsController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('PLATFORM_ADMIN')");
    }

    @Test
    void delegatesReadOnlyEndpointsAndValidatesPagination() {
        controller.health();
        controller.queues(0, 100);
        verify(diagnostics).health();
        verify(diagnostics).queues(0, 100);
        assertThrows(ResponseStatusException.class, () -> controller.queues(-1, 20));
        assertThrows(ResponseStatusException.class, () -> controller.queues(0, 0));
        assertThrows(ResponseStatusException.class, () -> controller.queues(0, 101));
    }
}
