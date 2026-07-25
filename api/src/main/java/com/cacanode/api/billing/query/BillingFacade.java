package com.cacanode.api.billing.query;

import com.cacanode.api.billing.api.BillingModuleApi;
import com.cacanode.api.billing.service.BillingPlanCatalog;
import com.cacanode.api.billing.service.BillingPeriods;
import com.cacanode.api.billing.config.BillingProperties;
import com.cacanode.api.billing.api.BillingDtos;
import com.cacanode.api.billing.api.BillingInterval;
import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.billing.api.PaymentOrderStatus;
import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.billing.gateway.PaymentGateway;
import com.cacanode.api.billing.gateway.PaymentGatewayException;
import com.cacanode.api.billing.model.BillingPaymentOrder;
import com.cacanode.api.billing.model.BillingSubscription;
import com.cacanode.api.billing.model.BillingWebhookEvent;
import com.cacanode.api.billing.model.EntitlementSnapshot;
import com.cacanode.api.billing.repository.BillingPaymentOrderRepository;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.repository.BillingWebhookEventRepository;
import com.cacanode.api.tenant.api.event.TenantCreatedEvent;
import com.cacanode.api.common.cache.BusinessCache;
import com.cacanode.api.common.cache.BusinessCacheInvalidationPublisher;
import com.cacanode.api.common.cache.CacheKeyFactory;
import com.cacanode.api.common.cache.VersionedJsonCache;
import com.cacanode.api.common.event.AuditLogEvent;
import com.cacanode.api.common.event.durable.DurableEventPublisher;
import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import com.cacanode.api.billing.api.event.BillingActivatedEvent;
import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.InternalServerErrorException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.document.api.DocumentApi;
import com.cacanode.api.tenant.api.ApplyTenantEntitlementsCommand;
import com.cacanode.api.tenant.api.TenantEntitlementApi;
import com.cacanode.api.tenant.api.TenantIdentityApi;
import com.cacanode.api.tenant.api.TenantPlan;
import com.cacanode.api.tenant.api.TenantStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingFacade implements BillingModuleApi {
    private static final String SUPERSEDED_PAYMENT_REASON = "Superseded by a successful payment";
    private static final Set<PaymentOrderStatus> OPEN_PAYMENT_STATUSES =
            Set.of(PaymentOrderStatus.PENDING, PaymentOrderStatus.PROCESSING);
    private static final Set<PaymentOrderStatus> ACCOUNT_PAYMENT_STATUSES =
            Set.of(PaymentOrderStatus.PENDING, PaymentOrderStatus.PROCESSING, PaymentOrderStatus.REVIEW);

    private final BillingPlanCatalog catalog;
    private final BillingProperties properties;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final BillingPaymentOrderRepository paymentRepository;
    private final BillingWebhookEventRepository webhookRepository;
    private final TenantEntitlementApi tenantModuleApi;
    private final TenantIdentityApi tenantIdentityApi;
    private final DocumentApi documentApi;
    private final PaymentGateway paymentGateway;
    private final BillingPeriods periods;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    @Autowired(required = false)
    private VersionedJsonCache businessCache;
    @Autowired(required = false)
    private CacheKeyFactory cacheKeyFactory;
    @Autowired(required = false)
    private BusinessCacheInvalidationPublisher businessInvalidationPublisher;
    @Autowired(required = false)
    private DurableEventPublisher durableEventPublisher;
    @Autowired(required = false)
    private ModuleEventInboxService inboxService;

    @Override
    public List<BillingDtos.PublicPlan> plans() {
        return catalog.publicPlans();
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTenantCreated(TenantCreatedEvent event) {
        if (inboxService != null && !inboxService.claim("billing.trial-subscription")) return;
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
        invalidateBilling(event.tenantId());
    }

    @Override
    @Transactional
    public BillingDtos.AccountResponse account(UUID tenantId) {
        if (businessCache == null || cacheKeyFactory == null) {
            return loadAccountAuthoritative(tenantId);
        }
        return businessCache.getOrLoad(
                BusinessCache.BILLING_ACCOUNT,
                cacheKeyFactory.build("billing-account", "tenant", tenantId.toString()),
                BillingDtos.AccountResponse.class,
                () -> loadAccountAuthoritative(tenantId)
        );
    }

    private BillingDtos.AccountResponse loadAccountAuthoritative(UUID tenantId) {
        BillingSubscription subscription = lockOrCreateTrial(tenantId);
        LocalDateTime now = utcNow();
        BillingPeriods.Period period = periods.currentQuotaPeriod(subscription, now);
        long messages = longQuery(
                "SELECT COALESCE((SELECT message_count FROM usage_metrics WHERE tenant_id = ? AND period_start = ?), 0)",
                tenantId, period.start());
        long verifiedApplications = longQuery(
                "SELECT COALESCE((SELECT verified_application_count FROM usage_metrics WHERE tenant_id = ? AND period_start = ?), 0)",
                tenantId, period.start());
        long cvAnalyses = longQuery(
                "SELECT COALESCE((SELECT cv_analysis_count FROM usage_metrics WHERE tenant_id = ? AND period_start = ?), 0)",
                tenantId, period.start());
        long interviewSeconds = longQuery(
                "SELECT COALESCE((SELECT interview_seconds FROM usage_metrics WHERE tenant_id = ? AND period_start = ?), 0)",
                tenantId, period.start());
        long activeJobReservations = longQuery("""
                SELECT COALESCE(SUM(reserved_amount), 0) FROM hiring_quota_reservations
                WHERE tenant_id = ? AND quota_kind = 'ACTIVE_JOB' AND state = 'RESERVED'
                """, tenantId);
        long interviewReservations = longQuery("""
                SELECT COALESCE(SUM(reserved_amount), 0) FROM hiring_quota_reservations
                WHERE tenant_id = ? AND quota_kind = 'INTERVIEW_SECONDS' AND state = 'RESERVED' AND expires_at > ?
                """, tenantId, now);
        long recruitmentStorageUsed = longQuery("""
                SELECT COALESCE(SUM(settled_amount), 0) FROM hiring_quota_reservations
                WHERE tenant_id = ? AND quota_kind = 'RECRUITMENT_STORAGE' AND state = 'COMMITTED'
                """, tenantId);
        long recruitmentStorageReserved = longQuery("""
                SELECT COALESCE(SUM(reserved_amount), 0) FROM hiring_quota_reservations
                WHERE tenant_id = ? AND quota_kind = 'RECRUITMENT_STORAGE' AND state = 'RESERVED' AND expires_at > ?
                """, tenantId, now);
        var documentUsage = documentApi.usage(tenantId);
        long documents = documentUsage.documentCount();
        long storageBytes = documentUsage.storageBytes();
        long members = tenantIdentityApi.memberUsage(tenantId, now);
        EntitlementSnapshot entitlements = subscription.getEntitlementSnapshot();
        BillingPaymentOrder pending = paymentRepository
                .findFirstByTenantIdAndStatusInOrderByCreatedAtDesc(tenantId, ACCOUNT_PAYMENT_STATUSES).orElse(null);
        long storageMb = storageBytes == 0 ? 0 : (storageBytes + 1024L * 1024L - 1) / (1024L * 1024L);
        return new BillingDtos.AccountResponse(
                subscription.getPlanCode(), subscription.getStatus(), subscription.getBillingInterval(),
                subscription.getTrialEndsAt(), subscription.getPaidThroughAt(), subscription.getGraceEndsAt(),
                period.start(), period.end(), usage(messages, entitlements.maxMessages()),
                usage(documents, entitlements.maxDocuments()), usage(members, entitlements.maxTeamMembers()),
                usage(storageMb, entitlements.maxStorageMb()),
                hiringUsage(0, activeJobReservations, entitlements.maxActiveJobs()),
                hiringUsage(verifiedApplications, 0, entitlements.maxVerifiedApplications()),
                hiringUsage(interviewSeconds, interviewReservations, entitlements.maxInterviewSeconds()),
                hiringUsage(cvAnalyses, 0, entitlements.maxCvAnalyses()),
                hiringUsage(recruitmentStorageUsed, recruitmentStorageReserved,
                        entitlements.maxRecruitmentStorageBytes()),
                features(entitlements),
                pending == null ? null : paymentResponse(pending), subscription.isCancelAtPeriodEnd());
    }

    @Override
    @Transactional(noRollbackFor = PaymentGatewayException.class)
    public BillingDtos.CheckoutResponse createCheckout(
            UUID tenantId, UUID userId, BillingDtos.CheckoutRequest request, String idempotencyKey
    ) {
        lockOrCreateTrial(tenantId);
        if (request.planCode() != BillingPlanCode.PRO && request.planCode() != BillingPlanCode.BUSINESS) {
            throw new BadRequestException(request.planCode() == BillingPlanCode.ENTERPRISE
                    ? "Enterprise is provisioned through sales" : "Only Pro and Business can be purchased through checkout");
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
        order.setRequestedPlan(request.planCode());
        order.setBillingInterval(request.interval());
        order.setAmountVnd(catalog.price(request.planCode(), request.interval()));
        order.setCatalogVersion(catalog.version());
        order.setEntitlementSnapshot(catalog.entitlements(request.planCode()));
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
        invalidateBilling(tenantId);

        try {
            PaymentGateway.CreatedPayment created = paymentGateway.createPayment(new PaymentGateway.CreatePayment(
                    order.getOrderCode(), order.getAmountVnd(), transferDescription(order.getOrderCode()),
                    "CacaNode " + displayPlan(request.planCode()) + " " + request.interval().name().toLowerCase(),
                    appendPaymentId(properties.getFrontendReturnUrl(), order.getId()),
                    appendPaymentId(properties.getFrontendCancelUrl(), order.getId()), expiresAt));
            order.setPaymentLinkId(created.paymentLinkId());
            order.setCheckoutUrl(created.checkoutUrl());
            order.setExpiresAt(created.expiresAt());
            return checkoutResponse(order);
        } catch (PaymentGatewayException exception) {
            order.setStatus(PaymentOrderStatus.FAILED);
            order.setFailureReason(exception.getMessage());
            invalidateBilling(tenantId);
            throw exception;
        }
    }

    @Override
    @Transactional
    public BillingDtos.PaymentResponse payment(UUID tenantId, UUID paymentId) {
        BillingPaymentOrder candidate = paymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found"));
        BillingSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing account was not found"));
        BillingPaymentOrder order = paymentRepository.findByIdAndTenantIdForUpdate(candidate.getId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found"));
        reconcileOpenOrder(order, subscription);
        return paymentResponse(order);
    }

    @Override
    @Transactional
    public BillingDtos.DowngradeResponse downgrade(UUID tenantId) {
        BillingSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Billing account was not found"));
        if (subscription.getPlanCode() == BillingPlanCode.TRIAL) {
            moveToStarter(subscription, utcNow());
            return new BillingDtos.DowngradeResponse(false, utcNow(), loadAccountAuthoritative(tenantId));
        }
        if (subscription.getPlanCode() == BillingPlanCode.PRO
                || subscription.getPlanCode() == BillingPlanCode.BUSINESS) {
            subscription.setCancelAtPeriodEnd(true);
            invalidateBilling(tenantId);
            return new BillingDtos.DowngradeResponse(true, subscription.getGraceEndsAt(),
                    loadAccountAuthoritative(tenantId));
        }
        return new BillingDtos.DowngradeResponse(false, null, loadAccountAuthoritative(tenantId));
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

        BillingPaymentOrder candidate = paymentRepository.findByOrderCode(verified.orderCode()).orElse(null);
        if (candidate == null) {
            saveWebhook(null, verified.providerReference(), payloadHash, true, "UNKNOWN_ORDER", null);
            return;
        }
        BillingSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(candidate.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Billing account was not found"));
        BillingPaymentOrder order = paymentRepository.findByOrderCodeForUpdate(verified.orderCode()).orElse(null);
        if (order == null || webhookRepository.findByPayloadHash(payloadHash).isPresent()) {
            return;
        }
        invalidateBilling(order.getTenantId());
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
        activatePaidOrder(order, subscription, verified.providerReference(), utcNow());
        saveWebhook(order, verified.providerReference(), payloadHash, true,
                order.getStatus() == PaymentOrderStatus.REVIEW ? "REVIEW" : "PAID", order.getFailureReason());
    }

    @Transactional
    public void reconcile(UUID paymentId) {
        BillingPaymentOrder candidate = paymentRepository.findById(paymentId).orElse(null);
        if (candidate == null) return;
        BillingSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(candidate.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Billing account was not found"));
        BillingPaymentOrder order = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        reconcileOpenOrder(order, subscription);
    }

    private void reconcileOpenOrder(BillingPaymentOrder order, BillingSubscription subscription) {
        if (order == null || !isReconcilable(order)) {
            return;
        }
        invalidateBilling(order.getTenantId());
        PaymentGateway.ProviderPayment provider = paymentGateway.getPayment(order.getOrderCode());
        if (provider.orderCode() != order.getOrderCode()
                || provider.amount() != order.getAmountVnd()
                || order.getPaymentLinkId() == null
                || !order.getPaymentLinkId().equals(provider.paymentLinkId())) {
            order.setStatus(PaymentOrderStatus.REVIEW);
            order.setFailureReason("Reconciliation identity or amount mismatch");
            return;
        }
        if (provider.status() == PaymentOrderStatus.PAID) {
            if (provider.amountPaid() != order.getAmountVnd()) {
                order.setStatus(PaymentOrderStatus.REVIEW);
                order.setFailureReason("Paid amount did not match the payment order");
                return;
            }
            activatePaidOrder(order, subscription, provider.providerReference(), utcNow());
            return;
        }
        order.setStatus(provider.status());
        order.setProviderReference(provider.providerReference());
        order.setFailureReason(null);
    }

    private boolean isReconcilable(BillingPaymentOrder order) {
        return OPEN_PAYMENT_STATUSES.contains(order.getStatus())
                || (order.getStatus() == PaymentOrderStatus.CANCELLED
                && SUPERSEDED_PAYMENT_REASON.equals(order.getFailureReason()));
    }

    private void activatePaidOrder(BillingPaymentOrder order, BillingSubscription subscription,
                                   String providerReference, LocalDateTime paidAt) {
        if (order.getStatus() == PaymentOrderStatus.PAID) {
            return;
        }
        if (paymentRepository.existsSupersedingPaidOrder(
                order.getTenantId(), order.getId(), order.getCreatedAt())) {
            order.setStatus(PaymentOrderStatus.REVIEW);
            order.setProviderReference(providerReference);
            order.setFailureReason("A newer payment already activated this billing account");
            return;
        }
        BillingPlanCode requestedPlan = order.getRequestedPlan();
        boolean extendsExisting = subscription.getPlanCode() == requestedPlan
                && (requestedPlan == BillingPlanCode.PRO || requestedPlan == BillingPlanCode.BUSINESS)
                && subscription.getPaidThroughAt() != null
                && (subscription.getStatus() == BillingStatus.ACTIVE || subscription.getStatus() == BillingStatus.GRACE);
        LocalDateTime base = extendsExisting ? subscription.getPaidThroughAt() : paidAt;
        LocalDateTime paidThrough = order.getBillingInterval() == BillingInterval.MONTHLY
                ? base.plusMonths(1) : base.plusYears(1);
        subscription.setPlanCode(requestedPlan);
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
        paymentRepository.cancelOtherOpenOrders(
                order.getTenantId(), order.getId(), OPEN_PAYMENT_STATUSES,
                PaymentOrderStatus.CANCELLED, SUPERSEDED_PAYMENT_REASON, paidAt);
        applyProjection(subscription);
        eventPublisher.publishEvent(AuditLogEvent.builder(this)
                .tenantId(order.getTenantId()).userId(order.getUserId())
                .action(LogAction.BILLING_PAYMENT_ACTIVATED).resourceType("billing_payment")
                .resourceId(order.getId()).metadata(Map.of(
                        "interval", order.getBillingInterval().name(),
                        "planCode", requestedPlan.name(),
                        "amountVnd", order.getAmountVnd(),
                        "paidThroughAt", paidThrough.toString()))
                .build());
        publishBusinessEvent("billing.activated.v1", new BillingActivatedEvent(
                order.getTenantId(), order.getUserId(), order.getId(),
                order.getBillingInterval().name(), paidThrough, requestedPlan.name()));
        invalidateBilling(order.getTenantId());
    }

    public void moveToStarter(BillingSubscription subscription, LocalDateTime effectiveAt) {
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
        invalidateBilling(subscription.getTenantId());
    }

    public void applyProjection(BillingSubscription subscription) {
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

    private void invalidateBilling(UUID tenantId) {
        if (businessInvalidationPublisher != null) {
            businessInvalidationPublisher.billing(tenantId);
        }
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

    private BillingDtos.HiringUsageItem hiringUsage(long used, long reserved, Long limit) {
        if (limit == null || limit < 0) {
            throw new HiringQuotaApi.HiringQuotaException(
                    "HIRING_QUOTA_NOT_CONFIGURED", "Hiring quota is not configured");
        }
        long total;
        try {
            total = Math.addExact(used, reserved);
        } catch (ArithmeticException overflow) {
            total = Long.MAX_VALUE;
        }
        return new BillingDtos.HiringUsageItem(used, reserved, limit, total > limit);
    }

    private BillingDtos.Features features(EntitlementSnapshot e) {
        return new BillingDtos.Features(e.apiAccess(), e.webhooks(), e.advancedAnalytics(), e.customBranding());
    }

    private BillingDtos.CheckoutResponse checkoutResponse(BillingPaymentOrder order) {
        return new BillingDtos.CheckoutResponse(order.getId(), order.getRequestedPlan(), order.getBillingInterval(),
                order.getAmountVnd(), order.getCheckoutUrl(), order.getExpiresAt());
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

    private String displayPlan(BillingPlanCode planCode) {
        String value = planCode.name().toLowerCase();
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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
        return LocalDateTime.now(clock);
    }

    private void publishBusinessEvent(String stableType, Object event) {
        if (durableEventPublisher != null) {
            durableEventPublisher.publish(stableType, 1, event);
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    private BillingSubscription lockOrCreateTrial(UUID tenantId) {
        BillingSubscription existing = subscriptionRepository.findByTenantIdForUpdate(tenantId).orElse(null);
        if (existing != null) return existing;
        LocalDateTime now = utcNow();
        BillingSubscription trial = new BillingSubscription();
        trial.setTenantId(tenantId);
        trial.setPlanCode(BillingPlanCode.TRIAL);
        trial.setStatus(BillingStatus.TRIAL);
        trial.setCatalogVersion(catalog.version());
        trial.setQuotaAnchorAt(now);
        trial.setTrialEndsAt(now.plusDays(14));
        trial.setEntitlementSnapshot(catalog.entitlements(BillingPlanCode.TRIAL));
        try {
            subscriptionRepository.saveAndFlush(trial);
            return trial;
        } catch (DataIntegrityViolationException race) {
            return subscriptionRepository.findByTenantIdForUpdate(tenantId).orElseThrow();
        }
    }
}
