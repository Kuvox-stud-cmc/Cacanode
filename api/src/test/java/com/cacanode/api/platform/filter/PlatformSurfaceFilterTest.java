package com.cacanode.api.platform.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSurfaceFilterTest {
    @Test
    void platformDiagnosticsResponsesAreAlwaysNoStore() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/platform/operations/health");
        var response = new MockHttpServletResponse();
        new PlatformSurfaceFilter(new ObjectMapper()).doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
