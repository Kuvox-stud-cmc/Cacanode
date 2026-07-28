package com.cacanode.api.platform;

import com.cacanode.api.platform.api.PlatformFailureApi;
import com.cacanode.api.platform.controller.PlatformFailureController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformFailureControllerTest {
    private final PlatformFailureApi failures = mock(PlatformFailureApi.class);
    private final PlatformFailureController controller = new PlatformFailureController(failures);

    @Test
    void isPlatformAdminOnlyAndFeatureFlagged() {
        ConditionalOnProperty condition = PlatformFailureController.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.prefix()).isEqualTo("app.platform-administration");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(PlatformFailureController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('PLATFORM_ADMIN')");
    }

    @Test
    void delegatesOnlyPlatformOwnedReadContracts() {
        UUID tenant = UUID.randomUUID();
        controller.summary(tenant);
        controller.failures("BILLING", tenant, "FAILED", "ERROR", 2, 25, "attempts", "asc");
        verify(failures).summary(tenant);
        verify(failures).failures("BILLING",
                new PlatformFailureApi.FailureQuery(tenant, "FAILED", "ERROR", 2, 25, "attempts", "asc"));
    }
}
