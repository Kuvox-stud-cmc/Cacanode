package com.cacanode.api.billing.service;

import com.cacanode.api.billing.api.BillingQuotaApi;
import com.cacanode.api.billing.api.MessageQuotaExceededException;
import com.cacanode.api.billing.api.event.QuotaExceededEvent;
import com.cacanode.api.billing.api.event.QuotaWarningEvent;
import com.cacanode.api.billing.api.BillingPlanCode;
import com.cacanode.api.billing.api.BillingStatus;
import com.cacanode.api.billing.model.BillingSubscription;
import com.cacanode.api.billing.model.UsageMetrics;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.repository.UsageMetricsRepository;
import com.cacanode.api.common.event.durable.DurableEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingQuotaService implements BillingQuotaApi {
    private final BillingSubscriptionRepository subscriptionRepository;
    private final UsageMetricsRepository usageRepository;
    private final BillingPeriods periods;
    private final BillingPlanCatalog catalog;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    @Autowired(required = false)
    private DurableEventPublisher durableEventPublisher;

    @Override
    @Transactional
    public QuotaConsumption consumeMessageQuota(UUID tenantId) {
        BillingSubscription subscription = lockOrCreateTrial(tenantId);
        var period = periods.currentQuotaPeriod(subscription, now());
        UsageMetrics usage = usageRepository.findByTenantIDAndPeriodStart(tenantId, period.start())
                .orElseGet(() -> newUsage(tenantId, period));
        Integer limit = subscription.getEntitlementSnapshot().maxMessages();
        if (limit != null && usage.getMessageCount() >= limit) {
            throw new MessageQuotaExceededException();
        }
        usage.setMessageCount(usage.getMessageCount() + 1);
        if (limit != null && usage.getMessageCount() >= Math.ceil(limit * 0.8)
                && !usage.isWarning80Sent()) {
            usage.setWarning80Sent(true);
            publishBusinessEvent("billing.quota.warning.v1",
                    new QuotaWarningEvent(tenantId, usage.getMessageCount(), limit));
        }
        if (limit != null && usage.getMessageCount() >= limit && !usage.isExceededSent()) {
            usage.setExceededSent(true);
            publishBusinessEvent("billing.quota.exceeded.v1",
                    new QuotaExceededEvent(tenantId, usage.getMessageCount(), limit));
        }
        usageRepository.save(usage);
        return new QuotaConsumption(UUID.randomUUID(), usage.getMessageCount(), limit);
    }

    @Override
    @Transactional
    public void rollbackMessageQuota(UUID tenantId, UUID consumptionId) {
        BillingSubscription subscription = subscriptionRepository.findByTenantIdForUpdate(tenantId).orElse(null);
        if (subscription == null) {
            return;
        }
        var period = periods.currentQuotaPeriod(subscription, now());
        usageRepository.findByTenantIDAndPeriodStart(tenantId, period.start()).ifPresent(usage -> {
            if (usage.getMessageCount() > 0) {
                usage.setMessageCount(usage.getMessageCount() - 1);
            }
        });
    }

    private BillingSubscription lockOrCreateTrial(UUID tenantId) {
        BillingSubscription existing = subscriptionRepository.findByTenantIdForUpdate(tenantId).orElse(null);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = now();
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

    private UsageMetrics newUsage(UUID tenantId, BillingPeriods.Period period) {
        UsageMetrics created = new UsageMetrics();
        created.setTenantID(tenantId);
        created.setPeriodYear(period.start().getYear());
        created.setPeriodMonth(period.start().getMonthValue());
        created.setPeriodStart(period.start());
        created.setPeriodEnd(period.end());
        return created;
    }

    private void publishBusinessEvent(String stableType, Object event) {
        if (durableEventPublisher != null) {
            durableEventPublisher.publish(stableType, 1, event);
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
