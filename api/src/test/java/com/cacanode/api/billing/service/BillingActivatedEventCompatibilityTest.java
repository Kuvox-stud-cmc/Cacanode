package com.cacanode.api.billing.service;

import com.cacanode.api.billing.api.event.BillingActivatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BillingActivatedEventCompatibilityTest {
    @Test
    void oldPayloadWithoutPlanCodeFallsBackToPro() throws Exception {
        String payload = """
                {
                  "tenantId":"%s",
                  "userId":"%s",
                  "paymentId":"%s",
                  "interval":"MONTHLY",
                  "paidThroughAt":"2026-08-23T10:15:30"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        BillingActivatedEvent event = mapper.readValue(payload, BillingActivatedEvent.class);

        assertNull(event.planCode());
        assertEquals("PRO", event.effectivePlanCode());
    }
}
