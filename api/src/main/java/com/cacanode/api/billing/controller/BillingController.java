package com.cacanode.api.billing.controller;

import com.cacanode.api.billing.api.BillingModuleApi;
import com.cacanode.api.billing.api.BillingDtos;
import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.common.exception.custom.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.access.AccessDeniedException;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BillingController extends BaseController {
    private final BillingModuleApi billingService;

    @GetMapping("/public/billing/plans")
    public List<BillingDtos.PublicPlan> plans() {
        return billingService.plans();
    }

    @GetMapping("/billing/account")
    public BillingDtos.AccountResponse account(HttpServletRequest request) {
        return billingService.account(getTenantId(request));
    }

    @PostMapping("/billing/checkouts")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingDtos.CheckoutResponse checkout(
            @Valid @RequestBody BillingDtos.CheckoutRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        requireTenantAdmin(request);
        return billingService.createCheckout(getTenantId(request), getUserId(request), body, idempotencyKey);
    }

    @GetMapping("/billing/payments/{paymentId}")
    public BillingDtos.PaymentResponse payment(@PathVariable UUID paymentId, HttpServletRequest request) {
        return billingService.payment(getTenantId(request), paymentId);
    }

    @PostMapping("/billing/downgrade")
    public BillingDtos.DowngradeResponse downgrade(HttpServletRequest request) {
        requireTenantAdmin(request);
        return billingService.downgrade(getTenantId(request));
    }

    @PostMapping("/public/billing/payos/webhook")
    public Map<String, Object> payOsWebhook(@RequestBody Map<String, Object> payload) {
        billingService.processPayOsWebhook(payload);
        return Map.of("success", true);
    }

    private void requireTenantAdmin(HttpServletRequest request) {
        if (!"TENANT_ADMIN".equals(getRole(request))) {
            throw new AccessDeniedException("Only tenant admins can manage billing");
        }
    }
}
