package com.cacanode.api.tenant.controller;

import com.cacanode.api.tenant.dto.CustomerAnswerPromptDtos;
import com.cacanode.api.tenant.service.CustomerAnswerPromptService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(CustomerAnswerPromptAuthorizationTest.Config.class)
class CustomerAnswerPromptAuthorizationTest {
    @Autowired
    private CustomerAnswerPromptController controller;

    @Autowired
    private CustomerAnswerPromptService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void regularUserIsDenied() {
        authenticate("ROLE_USER");
        HttpServletRequest request = request(UUID.randomUUID(), UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> controller.get(request));
    }

    @Test
    void tenantAdminCanReadPrompt() {
        authenticate("ROLE_TENANT_ADMIN");
        UUID tenantId = UUID.randomUUID();
        HttpServletRequest request = request(tenantId, UUID.randomUUID());
        var expected = new CustomerAnswerPromptDtos.Response(
                "Use a warm tone.", false, LocalDateTime.of(2026, 7, 14, 10, 0)
        );
        when(service.get(tenantId)).thenReturn(expected);

        assertEquals(expected, controller.get(request));
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user", "password", List.of(new SimpleGrantedAuthority(authority))
                )
        );
    }

    private HttpServletRequest request(UUID tenantId, UUID userId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("tenantId")).thenReturn(tenantId.toString());
        when(request.getAttribute("userId")).thenReturn(userId.toString());
        return request;
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CustomerAnswerPromptService customerAnswerPromptService() {
            return mock(CustomerAnswerPromptService.class);
        }

        @Bean
        CustomerAnswerPromptController customerAnswerPromptController(
                CustomerAnswerPromptService service
        ) {
            return new CustomerAnswerPromptController(service);
        }
    }
}
