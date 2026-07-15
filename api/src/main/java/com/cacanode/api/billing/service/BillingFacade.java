package com.cacanode.api.billing.service;

import com.cacanode.api.billing.api.BillingModuleApi;
import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.dto.BillingDtos;
import com.cacanode.api.billing.dto.UsageDto;
import com.cacanode.api.billing.enums.BillingInterval;
import com.cacanode.api.billing.enums.BillingPlanCode;
import com.cacanode.api.billing.enums.BillingStatus;
import com.cacanode.api.billing.enums.PaymentOrderStatus;
import com.cacanode.api.billing.gateway.PaymentGateway;
import com.cacanode.api.billing.gateway.PaymentGatewayException;
import com.cacanode.api.billing.model.BillingPaymentOrder;
import com.cacanode.api.billing.model.BillingSubscription;
import com.cacanode.api.billing.model.BillingWebhookEvent;
import com.cacanode.api.billing.model.EntitlementSnapshot;
import com.cacanode.api.billing.repository.BillingPaymentOrderRepository;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.repository.BillingWebhookEventRepository;
import com.cacanode.api.common.event.TenantCreatedEvent;
import com.cacanode.api.common.event.AuditLogEvent;
import com.cacanode.api.common.event.BillingActivatedEvent;
import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.InternalServerErrorException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.document.enums.DocumentStatus;
import com.cacanode.api.tenant.api.ApplyTenantEntitlementsCommand;
import com.cacanode.api.tenant.api.TenantModuleApi;
import com.cacanode.api.tenant.enums.TenantPlan;
import com.cacanode.api.tenant.enums.TenantStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingFacade implements BillingModuleApi {
    private static final Set<PaymentOrderStatus> OPEN_PAYMENT_STATUSES =
            Set.of(PaymentOrderStatus.PENDING, PaymentOrderStatus.PROCESSING);
    private static final Set<PaymentOrderStatus> ACCOUNT_PAYMENT_STATUSES =
            Set.of(PaymentOrderStatus.PENDING, PaymentOrderStatus.PROCESSING, PaymentOrderStatus.REVIEW);

    private final BillingService analyticsService;
    private final BillingPlanCatalog catalog;
    private final BillingProperties properties;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPaymentOrderRepository paymentRepository;
    private final BillingWebhookEventRepository webhookRepository;
    private final TenantModuleApi tenantModuleApi;
    private final PaymentGateway paymentGateway;
    private final BillingPeriods periods;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public UsageDto.DashboardSummary dashboardSummary(UUID tenantId) {
        return analyticsService.dashboardSummary(tenantId);
    }

    @Override
    public UsageDto.AnalyticsResponse analytics(UUID tenantId, UsageDto.AnalyticsScope scope, int days) {
        if (!tenantModuleApi.getEntitlements(tenantId).advancedAnalytics()) {
            throw new org.springframework.security.access.AccessDeniedException("Advanced analytics requires Pro");
        }
        return analyticsService.analytics(tenantId, scope, days);
    }

    @Override
    public List<BillingDtos.PublicPlan> plans() {
        return catalog.publicPlans();
    }

    @EventListener
    @Transactional
    public void onTenantCreated(TenantCreatedEvent event) {
        if (subscriptionRepository.findByTenantId(event.tenantId()).isPresent()) {
            return;
        }
        BillingSubscription subscription = new BillingSubscription();
        subscription.setTenantId(event.tenantId());
        subscription.setPlanCode(BillingPlanCode.TRIAL);
        subscription.setStatus(BillingStatus.TRIAL);
        subscription.setCatalogVersion(catalog.version());
        subscription.setQuotaAnchorAt(event.trialStartsAt());
        subscription.setTrialEndsAt(event.trialEndsAt());
        subscription.setEntitlementSnapshot(catalog.entitlements(BillingPlanCode.TRIAL));
        subscriptionRepository.save(subscription);
        applyProjection(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingDtos.AccountResponse account(UUID tenantId) {
        BillingSubscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing account was not found"));
        LocalDateTime now = utcNow();
        BillingPeriods.Period period = periods.currentQuotaPeriod(subscription, now);
        long messages = longQuery(
                "SELECT COALESCE((SELECT message_count FROM usage_metrics WHERE tenant_id = ? AND period_start = ?), 0)",
                tenantId, period.start());
        long documents = longQuery(
                "SELECT COUNT(*) FROM documents WHERE tenant_id = ? AND status <> ?",
                tenantId, DocumentStatus.FAILED.name());
        long storageBytes = longQuery(
                "SELECT COALESCE(SUM(file_size_bytes), 0) FROM documents WHERE tenant_id = ? AND status <> ?",
                tenantId, DocumentStatus.FAILED.name());
        long members = longQuery(
                "SELECT (SELECT COUNT(*) FROM users WHERE tenant_id = ? AND status = 'ACTIVE') + " +
                        "(SELECT COUNT(*) FROM invitations WHERE tenant_id = ? AND status = 'PENDING' AND expires_at > ?)",
                tenantId, tenantId, now);
        EntitlementSnapshot entitlements = subscription.getEntitlementSnapshot();
        BillingPaymentOrder pending = paymentRepository
                .findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(tenantId, ACCOUNT_PAYMENT_STATUSES).orElse(null);
        long storageMb = storageBytes == 0 ? 0 : (storageBytes + 1024L * 1024L - 1) / (1024L * 1024L);
        return new BillingDtos.AccountResponse(
                subscription.getPlanCode(), subscription.getStatus(), subscription.getBillingInterval(),
                subscription.getTrialEndsAt(), subscription.getPaidThroughAt(), subscription.getGraceEndsAt(),
                period.start(), period.end(), usage(messages, entitlements.maxMessages()),
                usage(documents, entitlements.maxDocuments()), usage(members, entitlements.maxTeamMembers()),
                usage(storageMb, entitlements.maxStorageMb()), features(entitlements),
                pending == null ? null : paymentResponse(pending), subscription.isCancelAtPeriodEnd());
    }

    @Override
    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public BillingDtos.CheckoutResponse createCheckout(
            UUID tenantId, UUID userId, BillingDtos.CheckoutRequest request, String idempotencyKey
    ) {
        subscriptionRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing account was not found"));
        if (request.planCode() != BillingPlanCode.PRO) {
            throw new BadRequestException(request.planCode() == BillingPlanCode.ENTERPRISE
                    ? "Enterprise is provisioned through sales" : "Only Pro can be purchased through checkout");
        }
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            BillingPaymentOrder existing = paymentRepository
                    .findByTenantIdAndClientIdempotencyKey(tenantId, normalizedKey).orElse(null);
            if (existing != null) {
                if (OPEN_PAYMENT_STATUSES.contains(existing.getStatus()) && existing.getExpiresAt().isAfter(utcNow())) {
                    return checkoutResponse(existing);
                }
                throw new BadRequestException("This idempotency key has already been used");
            }
        }

        LocalDateTime expiresAt = utcNow().plusMinutes(properties.getCheckoutMinutes());
        BillingPaymentOrder order = new BillingPaymentOrder();
        order.setTenantId(tenantId);
        order.setUserId(userId);
        order.setOrderCode(nextOrderCode());
        order.setRequestedPlan(BillingPlanCode.PRO);
        order.setBillingInterval(request.interval());
        order.setAmountVnd(catalog.price(request.planCode(), request.interval()));
        order.setCatalogVersion(catalog.version());
        order.setEntitlementSnapshot(catalog.entitlements(BillingPlanCode.PRO));
        order.setExpiresAt(expiresAt);
        order.setStatus(PaymentOrderStatus.PENDING);
        order.setClientIdempotencyKey(normalizedKey);
        try {
            paymentRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException exception) {
            if (normalizedKey != null) {
                return paymentRepository.findByTenantIdAndClientIdempotencyKey(tenantId, normalizedKey)
                        .map(this::checkoutResponse).orElseThrow(() -> exception);
            }
            throw exception;
        }

        try {
            PaymentGateway.CreatedPayment created = paymentGateway.createPayment(new PaymentGateway.CreatePayment(
                    order.getOrderCode(), order.getAmountVnd(), transferDescription(order.getOrderCode()),
                    request.interval() == BillingInterval.MONTHLY ? "CacaNode Pro monthly" : "CacaNode Pro annual",
                    appendPaymentId(properties.getFrontendReturnUrl(), order.getId()),
                    appendPaymentId(properties.getFrontendCancelUrl(), order.getId()), expiresAt));
            order.setPaymentLinkId(created.paymentLinkId());
            order.setCheckoutUrl(created.checkoutUrl());
            order.setExpiresAt(created.expiresAt());
            return checkoutResponse(order);
        } catch (PaymentGatewayException exception) {
            order.setStatus(PaymentOrderStatus.FAILED);
            order.setFailureReason(exception.getMessage());
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BillingDtos.PaymentResponse payment(UUID tenantId, UUID paymentId) {
        return paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .map(this::paymentResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found"));
    }

    @Override
    @Transactional
    public BillingDtos.DowngradeResponse downgrade(UUID tenantId) {
        BillingSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing account was not found"));
        if (subscription.getPlanCode() == BillingPlanCode.TRIAL) {
            moveToStarter(subscription, utcNow());
            return new BillingDtos.DowngradeResponse(false, utcNow(), account(tenantId));
        }
        if (subscription.getPlanCode() == BillingPlanCode.PRO) {
            subscription.setCancelAtPeriodEnd(true);
            return new BillingDtos.DowngradeResponse(true, subscription.getGraceEndsAt(), account(tenantId));
        }
        return new BillingDtos.DowngradeResponse(false, null, account(tenantId));
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public void processPayOsWebhook(Map<String, Object> payload) {
        String payloadHash = payloadHash(payload);
        BillingWebhookEvent duplicate = webhookRepository.findByPayloadHash(payloadHash).orElse(null);
        if (duplicate != null) {
            if (!duplicate.isSignatureValid()) {
                throw new BadRequestException("Invalid PayOS webhook signature");
            }
            return;
        }

        PaymentGateway.VerifiedWebhook verified;
        try {
            verified = paymentGateway.verifyWebhook(payload);
        } catch (PaymentGatewayException exception) {
            saveWebhook(null, null, payloadHash, false, "INVALID_SIGNATURE", exception.getMessage());
            throw new BadRequestException("Invalid PayOS webhook signature");
        }

        BillingPaymentOrder order = paymentRepository.findByOrderCodeForUpdate(verified.orderCode()).orElse(null);
        if (order == null) {
            saveWebhook(null, verified.providerReference(), payloadHash, true, "UNKNOWN_ORDER", null);
            return;
        }
        if (webhookRepository.findByPayloadHash(payloadHash).isPresent()) {
            return;
        }
        if (order.getStatus() == PaymentOrderStatus.PAID) {
            saveWebhook(order, verified.providerReference(), payloadHash, true, "DUPLICATE", null);
            return;
        }
        if (verified.amount() != order.getAmountVnd()
                || !"VND".equalsIgnoreCase(verified.currency())
                || order.getPaymentLinkId() == null
                || !order.getPaymentLinkId().equals(verified.paymentLinkId())) {
            order.setStatus(PaymentOrderStatus.REVIEW);
            order.setFailureReason("Provider identity, currency, or amount did not match the payment order");
            saveWebhook(order, verified.providerReference(), payloadHash, true, "REVIEW", order.getFailureReason());
            return;
        }
        if (!verified.successful()) {
            order.setStatus(PaymentOrderStatus.PROCESSING);
            saveWebhook(order, verified.providerReference(), payloadHash, true, "IGNORED", null);
            return;
        }
        activatePaidOrder(order, verified.providerReference(), utcNow());
        saveWebhook(order, verified.providerReference(), payloadHash, true, "PAID", null);
    }

    @Transactional
    public void reconcile(UUID paymentId) {
        BillingPaymentOrder order = paymentRepository.findById(paymentId).orElse(null);
        if (order == null || !OPEN_PAYMENT_STATUSES.contains(order.getStatus())) {
            return;
        }
        if (!order.getExpiresAt().isAfter(utcNow())) {
            order.setStatus(PaymentOrderStatus.EXPIRED);
            return;
        }
        PaymentGateway.ProviderPayment provider = paymentGateway.getPayment(order.getOrderCode());
        if (provider.orderCode() != order.getOrderCode()
                || provider.amount() != order.getAmountVnd()
                || order.getPaymentLinkId() == null
                || !order.getPaymentLinkId().equals(provider.paymentLinkId())) {
            order.setStatus(PaymentOrderStatus.REVIEW);
            order.setFailureReason("Reconciliation identity or amount mismatch");
            return;
        }
        if (provider.status() == PaymentOrderStatus.PAID && provider.amountPaid() == order.getAmountVnd()) {
            activatePaidOrder(order, provider.providerReference(), utcNow());
        } else {
            order.setStatus(provider.status());
        }
    }

    private void activatePaidOrder(BillingPaymentOrder order, String providerReference, LocalDateTime paidAt) {
        if (order.getStatus() == PaymentOrderStatus.PAID) {
            return;
        }
        BillingSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(order.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Billing account was not found"));
        boolean extendsExisting = subscription.getPlanCode() == BillingPlanCode.PRO
                && subscription.getPaidThroughAt() != null
                && (subscription.getStatus() == BillingStatus.ACTIVE || subscription.getStatus() == BillingStatus.GRACE);
        LocalDateTime base = extendsExisting ? subscription.getPaidThroughAt() : paidAt;
        LocalDateTime paidThrough = order.getBillingInterval() == BillingInterval.MONTHLY
                ? base.plusMonths(1) : base.plusYears(1);
        subscription.setPlanCode(BillingPlanCode.PRO);
        subscription.setStatus(BillingStatus.ACTIVE);
        subscription.setBillingInterval(order.getBillingInterval());
        subscription.setCatalogVersion(order.getCatalogVersion());
        subscription.setEntitlementSnapshot(order.getEntitlementSnapshot());
        subscription.setTrialEndsAt(null);
        subscription.setPaidThroughAt(paidThrough);
        subscription.setGraceEndsAt(paidThrough.plusDays(properties.getGraceDays()));
        subscription.setCancelAtPeriodEnd(false);
        subscription.setReminder7SentAt(null);
        subscription.setReminder3SentAt(null);
        subscription.setReminder1SentAt(null);
        subscription.setLastGraceReminderAt(null);
        if (!extendsExisting) {
            subscription.setQuotaAnchorAt(paidAt);
        }
        order.setStatus(PaymentOrderStatus.PAID);
        order.setProviderReference(providerReference);
        order.setPaidAt(paidAt);
        order.setFailureReason(null);
        applyProjection(subscription);
        eventPublisher.publishEvent(AuditLogEvent.builder(this)
                .tenantId(order.getTenantId()).userId(order.getUserId())
                .action(LogAction.BILLING_PAYMENT_ACTIVATED).resourceType("billing_payment")
                .resourceId(order.getId()).metadata(Map.of(
                        "interval", order.getBillingInterval().name(),
                        "amountVnd", order.getAmountVnd(),
                        "paidThroughAt", paidThrough.toString()))
                .build());
        eventPublisher.publishEvent(new BillingActivatedEvent(
                order.getTenantId(), order.getUserId(), order.getId(),
                order.getBillingInterval().name(), paidThrough));
    }

    void moveToStarter(BillingSubscription subscription, LocalDateTime effectiveAt) {
        subscription.setPlanCode(BillingPlanCode.STARTER);
        subscription.setStatus(BillingStatus.STARTER);
        subscription.setBillingInterval(null);
        subscription.setCatalogVersion(catalog.version());
        subscription.setEntitlementSnapshot(catalog.entitlements(BillingPlanCode.STARTER));
        subscription.setTrialEndsAt(null);
        subscription.setPaidThroughAt(null);
        subscription.setGraceEndsAt(null);
        subscription.setQuotaAnchorAt(effectiveAt);
        subscription.setCancelAtPeriodEnd(false);
        applyProjection(subscription);
    }

    void applyProjection(BillingSubscription subscription) {
        EntitlementSnapshot e = subscription.getEntitlementSnapshot();
        TenantPlan plan = TenantPlan.valueOf(subscription.getPlanCode().name());
        TenantStatus status = subscription.getPlanCode() == BillingPlanCode.TRIAL
                ? TenantStatus.TRIAL : TenantStatus.ACTIVE;
        tenantModuleApi.applyEntitlements(new ApplyTenantEntitlementsCommand(
                subscription.getTenantId(), plan, status, e.maxMessages(), e.maxDocuments(), e.maxTeamMembers(),
                e.maxStorageMb(), subscription.getTrialEndsAt(), subscription.getQuotaAnchorAt(),
                subscription.getPaidThroughAt(), subscription.getGraceEndsAt(), e.apiAccess(), e.webhooks(),
                e.advancedAnalytics(), e.customBranding()));
    }

    private void saveWebhook(BillingPaymentOrder order, String reference, String hash,
                             boolean signatureValid, String result, String failure) {
        BillingWebhookEvent event = new BillingWebhookEvent();
        event.setPaymentOrder(order);
        event.setProviderReference(reference);
        event.setPayloadHash(hash);
        event.setSignatureValid(signatureValid);
        event.setProcessingResult(result);
        event.setFailureReason(failure);
        event.setProcessedAt(utcNow());
        webhookRepository.save(event);
    }

    private BillingDtos.UsageItem usage(long used, Integer limit) {
        return new BillingDtos.UsageItem(used, limit, limit != null && used > limit);
    }

    private BillingDtos.Features features(EntitlementSnapshot e) {
        return new BillingDtos.Features(e.apiAccess(), e.webhooks(), e.advancedAnalytics(), e.customBranding());
    }

    private BillingDtos.CheckoutResponse checkoutResponse(BillingPaymentOrder order) {
        return new BillingDtos.CheckoutResponse(order.getId(), order.getCheckoutUrl(), order.getExpiresAt());
    }

    private BillingDtos.PaymentResponse paymentResponse(BillingPaymentOrder order) {
        return new BillingDtos.PaymentResponse(
                order.getId(), order.getStatus(), order.getRequestedPlan(), order.getBillingInterval(),
                order.getAmountVnd(), order.getCurrency(), order.getCheckoutUrl(), order.getExpiresAt(),
                order.getPaidAt(), order.getFailureReason());
    }

    private long nextOrderCode() {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('billing_order_code_seq')", Long.class);
        if (value == null) {
            throw new InternalServerErrorException("Unable to allocate payment order code");
        }
        return value;
    }

    private long longQuery(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 255) throw new BadRequestException("Idempotency-Key is too long");
        return normalized;
    }

    private String transferDescription(long orderCode) {
        String suffix = Long.toString(orderCode);
        suffix = suffix.substring(Math.max(0, suffix.length() - 6));
        return "CCN" + suffix;
    }

    private String appendPaymentId(String base, UUID paymentId) {
        return base + (base.contains("?") ? "&" : "?") + "paymentId=" + paymentId;
    }

    private String payloadHash(Map<String, Object> payload) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new InternalServerErrorException("Unable to hash PayOS webhook", exception);
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
