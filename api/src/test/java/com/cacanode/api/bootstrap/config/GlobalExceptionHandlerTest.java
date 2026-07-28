package com.cacanode.api.bootstrap.config;

import com.cacanode.api.tenant.api.WidgetOriginNotAllowedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {
    @Test
    void preservesResponseStatusExceptionStatusAndReason() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/v1/public/twilio/interviews/status");

        var response = new GlobalExceptionHandler().handleResponseStatusException(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "INVALID_TWILIO_SIGNATURE"), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("INVALID_TWILIO_SIGNATURE", response.getBody().getMessage());
    }

    @Test
    void reportsActionableWidgetOriginDenial() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/v1/public/widget/config");

        var response = new GlobalExceptionHandler().handleWidgetOriginNotAllowed(
                new WidgetOriginNotAllowedException(), request);

        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        assertEquals("Website origin is not allowed", response.getMessage());
        assertEquals("/api/v1/public/widget/config", response.getPath());
    }
}
