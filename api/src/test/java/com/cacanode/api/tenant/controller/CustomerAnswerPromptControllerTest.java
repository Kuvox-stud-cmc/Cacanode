package com.cacanode.api.tenant.controller;

import com.cacanode.api.tenant.dto.CustomerAnswerPromptDtos;
import com.cacanode.api.tenant.service.CustomerAnswerPromptService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerAnswerPromptControllerTest {
    @Test
    void controllerIsRestrictedToTenantAdmins() {
        PreAuthorize annotation = CustomerAnswerPromptController.class.getAnnotation(PreAuthorize.class);

        assertEquals("hasRole('TENANT_ADMIN')", annotation.value());
    }

    @Test
    void updateUsesAuthenticatedTenantAndActor() {
        CustomerAnswerPromptService service = mock(CustomerAnswerPromptService.class);
        CustomerAnswerPromptController controller = new CustomerAnswerPromptController(service);
        HttpServletRequest request = mock(HttpServletRequest.class);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var expected = new CustomerAnswerPromptDtos.Response(
                "Use a warm tone.", false, LocalDateTime.of(2026, 7, 14, 10, 0)
        );
        when(request.getAttribute("tenantId")).thenReturn(tenantId.toString());
        when(request.getAttribute("userId")).thenReturn(userId.toString());
        when(service.update(tenantId, userId, "Use a warm tone.")).thenReturn(expected);

        var response = controller.update(
                new CustomerAnswerPromptDtos.UpdateRequest("Use a warm tone."), request
        );

        assertEquals(expected, response);
        verify(service).update(tenantId, userId, "Use a warm tone.");
    }
}
