package com.cacanode.api.billing.gateway;

import com.cacanode.api.billing.config.BillingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
public class PayOsConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.billing.payos-enabled", havingValue = "true")
    PayOS payOS(BillingProperties properties) {
        BillingProperties.PayOs payos = properties.getPayos();
        if (payos.getClientId().isBlank() || payos.getApiKey().isBlank() || payos.getChecksumKey().isBlank()) {
            throw new IllegalStateException("PayOS is enabled but credentials are missing");
        }
        return new PayOS(payos.getClientId(), payos.getApiKey(), payos.getChecksumKey());
    }
}
