package com.cacanode.api.billing.gateway;

import com.cacanode.api.billing.config.BillingProperties;
import org.junit.jupiter.api.Test;
import vn.payos.PayOS;
import vn.payos.model.webhooks.ConfirmWebhookResponse;
import vn.payos.service.blocking.webhooks.WebhooksService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayOsWebhookRegistrationTest {
    @Test
    void confirmsConfiguredWebhookOnlyOnce() {
        PayOS payOS = mock(PayOS.class);
        WebhooksService webhooks = mock(WebhooksService.class);
        BillingProperties properties = new BillingProperties();
        properties.getPayos().setWebhookUrl("https://app.example.com/api/v1/public/billing/payos/webhook");
        when(payOS.webhooks()).thenReturn(webhooks);
        ConfirmWebhookResponse response = mock(ConfirmWebhookResponse.class);
        when(response.getWebhookUrl()).thenReturn(
                "https://app.example.com/api/v1/public/billing/payos/webhook");
        when(webhooks.confirm(properties.getPayos().getWebhookUrl())).thenReturn(response);
        PayOsWebhookRegistration registration = new PayOsWebhookRegistration(payOS, properties);

        assertTrue(registration.confirmIfNeeded());
        assertTrue(registration.confirmIfNeeded());

        verify(webhooks, times(1)).confirm(properties.getPayos().getWebhookUrl());
    }

    @Test
    void leavesReconciliationAvailableWhenConfirmationFails() {
        PayOS payOS = mock(PayOS.class);
        WebhooksService webhooks = mock(WebhooksService.class);
        BillingProperties properties = new BillingProperties();
        properties.getPayos().setWebhookUrl("https://app.example.com/api/v1/public/billing/payos/webhook");
        when(payOS.webhooks()).thenReturn(webhooks);
        when(webhooks.confirm(properties.getPayos().getWebhookUrl())).thenThrow(new RuntimeException("offline"));

        assertFalse(new PayOsWebhookRegistration(payOS, properties).confirmIfNeeded());
    }
}
