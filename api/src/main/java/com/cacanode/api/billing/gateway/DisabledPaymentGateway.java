package com.cacanode.api.billing.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.billing.payos-enabled", havingValue = "false", matchIfMissing = true)
public class DisabledPaymentGateway implements PaymentGateway {
    private PaymentGatewayException disabled() {
        return new PaymentGatewayException("PayOS checkout is currently disabled");
    }

    @Override
    public CreatedPayment createPayment(CreatePayment request) { throw disabled(); }

    @Override
    public VerifiedWebhook verifyWebhook(Map<String, Object> payload) { throw disabled(); }

    @Override
    public ProviderPayment getPayment(long orderCode) { throw disabled(); }
}
