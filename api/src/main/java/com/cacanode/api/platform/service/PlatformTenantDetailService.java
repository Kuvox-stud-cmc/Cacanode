package com.cacanode.api.platform.service;

import com.cacanode.api.analytics.api.PlatformAnalyticsReadApi;
import com.cacanode.api.billing.api.BillingPlatformReadApi;
import com.cacanode.api.common.api.operations.OperationalFailureReadApi;
import com.cacanode.api.platform.api.PlatformFailureApi;
import com.cacanode.api.recruitment.api.RecruitmentPlatformAdministrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformTenantDetailService {
    private final PlatformAnalyticsReadApi analytics;
    private final ObjectProvider<BillingPlatformReadApi> billingProvider;
    private final ObjectProvider<RecruitmentPlatformAdministrationApi> recruitmentProvider;
    private final PlatformFailureApi failures;

    public Detail tenant(UUID tenantId) {
        PlatformAnalyticsReadApi.TenantDetail base = analytics.tenant(tenantId);
        List<Warning> warnings = new ArrayList<>();
        BillingPlatformReadApi.Account billing = null;
        BillingPlatformReadApi billingReader = billingProvider.getIfAvailable();
        if (billingReader == null) warnings.add(new Warning("BILLING_UNAVAILABLE", null));
        else try {
            billing = billingReader.accountIfPresent(tenantId).orElse(null);
            if (billing == null) warnings.add(new Warning("BILLING_ACCOUNT_MISSING", null));
        } catch (RuntimeException unavailable) { warnings.add(new Warning("BILLING_UNAVAILABLE", null)); }

        RecruitmentPlatformAdministrationApi.Activation recruitment = null;
        RecruitmentPlatformAdministrationApi recruitmentReader = recruitmentProvider.getIfAvailable();
        if (recruitmentReader == null) warnings.add(new Warning("RECRUITMENT_DISABLED", null));
        else try { recruitment = recruitmentReader.activation(tenantId); }
        catch (RuntimeException unavailable) { warnings.add(new Warning("RECRUITMENT_UNAVAILABLE", null)); }

        PlatformFailureApi.Recent recent = failures.recent(tenantId, 10);
        recent.warnings().forEach(warning -> warnings.add(new Warning(warning.code(), warning.source())));
        return new Detail(base.generatedAt(), base.tenantId(), base.name(), base.status(), base.plan(),
                base.createdAt(), base.updatedAt(), base.aggregates(), billing, recruitment,
                recent.items(), base.freshness(), !warnings.isEmpty(), warnings);
    }

    public record Warning(String code, OperationalFailureReadApi.Source source) {}
    public record Detail(LocalDateTime generatedAt, UUID tenantId, String name, String status, String plan,
                         LocalDateTime createdAt, LocalDateTime updatedAt,
                         PlatformAnalyticsReadApi.TenantAggregates aggregates,
                         BillingPlatformReadApi.Account billing,
                         RecruitmentPlatformAdministrationApi.Activation recruitment,
                         List<OperationalFailureReadApi.Failure> recentFailures,
                         PlatformAnalyticsReadApi.Freshness freshness, boolean partial, List<Warning> warnings) {
        public Detail { recentFailures = List.copyOf(recentFailures); warnings = List.copyOf(warnings); }
    }
}
