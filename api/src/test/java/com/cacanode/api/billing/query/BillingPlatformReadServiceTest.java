package com.cacanode.api.billing.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingPlatformReadServiceTest {
    @Test
    void unlimitedQuotaHasNoPercentageAndCannotBeOverLimit() {
        var quota = BillingPlatformReadService.quota(100, 20, null);
        assertThat(quota.unlimited()).isTrue();
        assertThat(quota.utilizationPercentage()).isNull();
        assertThat(quota.overLimit()).isFalse();
    }

    @Test
    void zeroLimitUsesControlledPercentageRules() {
        assertThat(BillingPlatformReadService.quota(0, 0, 0L).utilizationPercentage()).isZero();
        var over = BillingPlatformReadService.quota(1, 0, 0L);
        assertThat(over.utilizationPercentage()).isEqualTo(100);
        assertThat(over.overLimit()).isTrue();
    }
}
