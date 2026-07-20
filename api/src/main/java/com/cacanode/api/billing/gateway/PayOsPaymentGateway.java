package com.cacanode.api.billing.gateway;

import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.enums.PaymentOrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.exception.InternalServerException;
import vn.payos.exception.TooManyRequestsException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.WebhookData;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Supplier;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@ConditionalOnProperty(name = "app.billing.payos-enabled", havingValue = "true")
@RequiredArgsConstructor
public class PayOsPaymentGateway implements PaymentGateway {
    private final PayOS payOS;
    private final BillingProperties properties;
    private final MeterRegistry meterRegistry;
    private final PayOsWebhookRegistration webhookRegistration;

    @Override
    public CreatedPayment createPayment(CreatePayment request) {
        webhookRegistration.confirmIfNeeded();
        CreatePaymentLinkRequest providerRequest = CreatePaymentLinkRequest.builder()
                .orderCode(request.orderCode())
                .amount(request.amountVnd())
                .description(request.description())
                .returnUrl(request.returnUrl())
                .cancelUrl(request.cancelUrl())
                .expiredAt(request.expiresAt().toEpochSecond(ZoneOffset.UTC))
                .item(PaymentLinkItem.builder()
                        .name(request.itemName()).quantity(1).price(request.amountVnd()).build())
                .build();
        var response = retry("create", () -> payOS.paymentRequests().create(providerRequest));
        return new CreatedPayment(
                response.getPaymentLinkId(), response.getCheckoutUrl(),
                epoch(response.getExpiredAt(), request.expiresAt()));
    }

    @Override
    public VerifiedWebhook verifyWebhook(Map<String, Object> payload) {
        try {
            WebhookData data = payOS.webhooks().verify(payload);
            return new VerifiedWebhook(
                    data.getOrderCode(), data.getAmount(), data.getCurrency(), data.getPaymentLinkId(),
                    data.getReference(), "00".equals(data.getCode()));
        } catch (RuntimeException exception) {
            meterRegistry.counter("billing.payos.webhook.invalid").increment();
            throw new PaymentGatewayException("PayOS webhook signature is invalid", exception);
        }
    }

    @Override
    public ProviderPayment getPayment(long orderCode) {
        PaymentLink payment = retry("get", () -> payOS.paymentRequests().get(orderCode));
        String reference = payment.getTransactions() == null || payment.getTransactions().isEmpty()
                ? null : payment.getTransactions().get(payment.getTransactions().size() - 1).getReference();
        return new ProviderPayment(
                payment.getOrderCode(), payment.getId(), payment.getAmount(), payment.getAmountPaid(),
                status(payment.getStatus()), reference);
    }

    private PaymentOrderStatus status(PaymentLinkStatus status) {
        return switch (status) {
            case PENDING -> PaymentOrderStatus.PENDING;
            case PROCESSING, UNDERPAID -> PaymentOrderStatus.PROCESSING;
            case PAID -> PaymentOrderStatus.PAID;
            case CANCELLED -> PaymentOrderStatus.CANCELLED;
            case EXPIRED -> PaymentOrderStatus.EXPIRED;
            case FAILED -> PaymentOrderStatus.FAILED;
        };
    }

    private <T> T retry(String operation, Supplier<T> call) {
        int attempts = Math.max(1, properties.getPayos().getMaxRetries());
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                T result = call.get();
                meterRegistry.counter("billing.payos.requests", "operation", operation, "result", "success").increment();
                return result;
            } catch (TooManyRequestsException | InternalServerException exception) {
                last = exception;
                meterRegistry.counter("billing.payos.retries", "operation", operation,
                        "reason", exception instanceof TooManyRequestsException ? "rate_limit" : "server_error").increment();
                if (attempt < attempts) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new PaymentGatewayException("PayOS request interrupted", interrupted);
                    }
                }
            } catch (RuntimeException exception) {
                meterRegistry.counter("billing.payos.requests", "operation", operation, "result", "failure").increment();
                throw new PaymentGatewayException("PayOS request failed", exception);
            }
        }
        meterRegistry.counter("billing.payos.requests", "operation", operation, "result", "failure").increment();
        throw new PaymentGatewayException("PayOS request failed after retries", last);
    }

    private LocalDateTime epoch(Long seconds, LocalDateTime fallback) {
        return seconds == null ? fallback : LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC);
    }
}
