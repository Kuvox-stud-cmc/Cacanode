package com.cacanode.api.billing.query;

import com.cacanode.api.billing.api.BillingPlatformReadApi;
import com.cacanode.api.billing.model.BillingSubscription;
import com.cacanode.api.billing.repository.BillingSubscriptionRepository;
import com.cacanode.api.billing.service.BillingPeriods;
import com.cacanode.api.document.api.DocumentApi;
import com.cacanode.api.tenant.api.TenantIdentityApi;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingPlatformReadService implements BillingPlatformReadApi {
    private final BillingSubscriptionRepository subscriptions;
    private final BillingPeriods periods;
    private final DocumentApi documents;
    private final TenantIdentityApi tenants;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public Optional<Account> accountIfPresent(UUID tenantId) {
        return subscriptions.findByTenantId(tenantId).map(subscription -> account(tenantId, subscription));
    }

    private Account account(UUID tenantId, BillingSubscription subscription) {
        LocalDateTime now = LocalDateTime.now(clock);
        BillingPeriods.Period period = periods.currentQuotaPeriod(subscription, now);
        var usage = documents.usage(tenantId);
        var limits = subscription.getEntitlementSnapshot();
        Map<String, Quota> quotas = new LinkedHashMap<>();
        quotas.put("messages", quota(metric(tenantId, period.start(), "message_count"), 0, longValue(limits.maxMessages())));
        quotas.put("documents", quota(usage.documentCount(), 0, longValue(limits.maxDocuments())));
        quotas.put("teamMembers", quota(tenants.memberUsage(tenantId, now), 0, longValue(limits.maxTeamMembers())));
        long storageMb = usage.storageBytes() == 0 ? 0 : (usage.storageBytes() + 1024L * 1024L - 1) / (1024L * 1024L);
        quotas.put("storage", quota(storageMb, 0, longValue(limits.maxStorageMb())));
        quotas.put("activeJobs", quota(0, reservations(tenantId, "ACTIVE_JOB", "RESERVED", now), limits.maxActiveJobs()));
        quotas.put("verifiedApplications", quota(metric(tenantId, period.start(), "verified_application_count"), 0, limits.maxVerifiedApplications()));
        quotas.put("interviewSeconds", quota(metric(tenantId, period.start(), "interview_seconds"), reservations(tenantId, "INTERVIEW_SECONDS", "RESERVED", now), limits.maxInterviewSeconds()));
        quotas.put("cvAnalyses", quota(metric(tenantId, period.start(), "cv_analysis_count"), 0, limits.maxCvAnalyses()));
        quotas.put("recruitmentStorage", quota(reservations(tenantId, "RECRUITMENT_STORAGE", "COMMITTED", now), reservations(tenantId, "RECRUITMENT_STORAGE", "RESERVED", now), limits.maxRecruitmentStorageBytes()));
        return new Account(subscription.getPlanCode().name(), subscription.getStatus().name(),
                period.start(), period.end(), quotas);
    }

    private long metric(UUID tenantId, LocalDateTime periodStart, String column) {
        Long value = jdbc.queryForObject("SELECT COALESCE((SELECT " + column + " FROM usage_metrics WHERE tenant_id=? AND period_start=?),0)", Long.class, tenantId, periodStart);
        return value == null ? 0 : value;
    }

    private long reservations(UUID tenantId, String kind, String state, LocalDateTime now) {
        String expiry = "RESERVED".equals(state) ? " AND expires_at>?" : "";
        Object[] args = "RESERVED".equals(state) ? new Object[]{tenantId, kind, state, now} : new Object[]{tenantId, kind, state};
        Long value = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN state='COMMITTED' THEN settled_amount ELSE reserved_amount END),0) FROM hiring_quota_reservations WHERE tenant_id=? AND quota_kind=? AND state=?" + expiry, Long.class, args);
        return value == null ? 0 : value;
    }

    static Quota quota(long used, long reserved, Long limit) {
        long total = Math.max(0, used) + Math.max(0, reserved);
        if (limit == null) return new Quota(used, reserved, null, null, true, false);
        boolean over = total > limit;
        double percentage = limit == 0 ? (total == 0 ? 0 : 100) : Math.min(100, total * 100.0 / limit);
        return new Quota(used, reserved, limit, percentage, false, over);
    }

    private static Long longValue(Integer value) { return value == null ? null : value.longValue(); }
}
