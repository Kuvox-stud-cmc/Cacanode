package com.cacanode.api.billing.gateway;

import com.cacanode.api.billing.config.BillingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j(topic = "PAYOS-WEBHOOK")
@Component
@ConditionalOnProperty(name = "app.billing.payos-enabled", havingValue = "true")
@RequiredArgsConstructor
public class PayOsWebhookRegistration {
    private final PayOS payOS;
    private final BillingProperties properties;
    private final AtomicBoolean confirmed = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void confirmAtStartup() {
        confirmIfNeeded();
    }

    public boolean confirmIfNeeded() {
        if (confirmed.get()) {
            return true;
        }
        synchronized (confirmed) {
            if (confirmed.get()) {
                return true;
            }
            String webhookUrl = properties.getPayos().getWebhookUrl();
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.error("PAYOS_WEBHOOK_URL is not configured; payment reconciliation remains available");
                return false;
            }
            try {
                var response = payOS.webhooks().confirm(webhookUrl);
                String confirmedUrl = response == null ? null : response.getWebhookUrl();
                if (confirmedUrl == null || !sameUrl(webhookUrl, confirmedUrl)) {
                    log.error("PayOS confirmed an unexpected webhook URL: {}", confirmedUrl);
                    return false;
                }
                confirmed.set(true);
                log.info("PayOS webhook confirmed at {}", confirmedUrl);
                return true;
            } catch (RuntimeException exception) {
                log.warn("Unable to confirm PayOS webhook; payment reconciliation remains available: {}",
                        exception.getMessage());
                return false;
            }
        }
    }

    private boolean sameUrl(String expected, String actual) {
        return stripTrailingSlash(expected).equals(stripTrailingSlash(actual));
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
