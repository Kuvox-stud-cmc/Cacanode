package com.cacanode.api.tenant.controller;

import com.cacanode.api.tenant.enums.TicketPriority;
import com.cacanode.api.tenant.enums.TicketSource;
import com.cacanode.api.tenant.enums.TicketStatus;
import com.cacanode.api.tenant.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketControllerTest {

    @Test
    void listForwardsTenantFullFiltersAndPaging() {
        TicketService service = mock(TicketService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        UUID tenantId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        when(request.getAttribute("tenantId")).thenReturn(tenantId.toString());
        when(service.list(
                tenantId, TicketStatus.IN_PROGRESS, TicketPriority.HIGH,
                TicketSource.CUSTOM_API, assigneeId, false, 2, 50
        )).thenReturn(Page.empty());

        var response = new TicketController(service).list(
                TicketStatus.IN_PROGRESS,
                TicketPriority.HIGH,
                TicketSource.CUSTOM_API,
                assigneeId,
                false,
                2,
                50,
                request
        );

        assertTrue(response.isEmpty());
        verify(service).list(
                tenantId, TicketStatus.IN_PROGRESS, TicketPriority.HIGH,
                TicketSource.CUSTOM_API, assigneeId, false, 2, 50
        );
    }

    @Test
    void listNormalizesMissingUnassignedFlagToFalse() {
        TicketService service = mock(TicketService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        UUID tenantId = UUID.randomUUID();
        when(request.getAttribute("tenantId")).thenReturn(tenantId.toString());
        when(service.list(tenantId, null, null, null, null, false, null, null))
                .thenReturn(Page.empty());

        var response = new TicketController(service).list(
                null, null, null, null, null, null, null, request
        );

        assertTrue(response.isEmpty());
        verify(service).list(tenantId, null, null, null, null, false, null, null);
    }
}
