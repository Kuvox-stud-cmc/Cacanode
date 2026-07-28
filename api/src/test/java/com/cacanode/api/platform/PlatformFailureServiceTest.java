package com.cacanode.api.platform;

import com.cacanode.api.analytics.api.PlatformAnalyticsReadApi;
import com.cacanode.api.common.api.operations.OperationalFailureReadApi;
import com.cacanode.api.platform.api.PlatformFailureApi;
import com.cacanode.api.platform.service.PlatformFailureService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformFailureServiceTest {
    private static final UUID TENANT = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final PlatformAnalyticsReadApi analytics = mock(PlatformAnalyticsReadApi.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-28T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void isolatesUnavailableSourcesWithoutBreakingExactOwnerSummaries() {
        when(analytics.tenantLabel(TENANT)).thenReturn(Optional.of(new PlatformAnalyticsReadApi.TenantLabel(TENANT, "Acme")));
        FakeReader reader = new FakeReader(EnumSet.allOf(OperationalFailureReadApi.Source.class));
        reader.unavailable = OperationalFailureReadApi.Source.WEBHOOKS;
        PlatformFailureApi.Summary result = new PlatformFailureService(List.of(reader), analytics, clock).summary(TENANT);
        assertThat(result.partial()).isTrue();
        assertThat(result.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo("FAILURE_SOURCE_UNAVAILABLE");
            assertThat(warning.source()).isEqualTo(OperationalFailureReadApi.Source.WEBHOOKS);
        });
        assertThat(result.sources()).filteredOn(item -> item.source() == OperationalFailureReadApi.Source.BILLING)
                .singleElement().extracting(PlatformFailureApi.SourceSummary::total).isEqualTo(2L);
        assertThat(result.sources()).filteredOn(item -> item.source() == OperationalFailureReadApi.Source.WEBHOOKS)
                .singleElement().extracting(PlatformFailureApi.SourceSummary::total).isEqualTo(0L);
    }

    @Test
    void validatesTenantPagingEnumsAndSortsBeforeOwnerExecution() {
        when(analytics.tenantLabel(TENANT)).thenReturn(Optional.empty());
        PlatformFailureService service = new PlatformFailureService(List.of(), analytics, clock);
        assertThatThrownBy(() -> service.summary(TENANT)).isInstanceOf(IllegalArgumentException.class);
        when(analytics.tenantLabel(TENANT)).thenReturn(Optional.of(new PlatformAnalyticsReadApi.TenantLabel(TENANT, "Acme")));
        assertInvalid(service, new PlatformFailureApi.FailureQuery(TENANT, null, null, -1, 20, "lastSeenAt", "desc"));
        assertInvalid(service, new PlatformFailureApi.FailureQuery(TENANT, null, null, 0, 101, "lastSeenAt", "desc"));
        assertInvalid(service, new PlatformFailureApi.FailureQuery(TENANT, "INVALID", null, 0, 20, "lastSeenAt", "desc"));
        assertInvalid(service, new PlatformFailureApi.FailureQuery(TENANT, null, "INVALID", 0, 20, "lastSeenAt", "desc"));
        assertInvalid(service, new PlatformFailureApi.FailureQuery(TENANT, null, null, 0, 20, "rawError", "desc"));
        assertInvalid(service, new PlatformFailureApi.FailureQuery(TENANT, null, null, 0, 20, "lastSeenAt", "sideways"));
    }

    @Test
    void recentRequestsAreBoundedPerSourceAndMergedToTen() {
        when(analytics.tenantLabel(TENANT)).thenReturn(Optional.of(new PlatformAnalyticsReadApi.TenantLabel(TENANT, "Acme")));
        FakeReader reader = new FakeReader(EnumSet.allOf(OperationalFailureReadApi.Source.class));
        PlatformFailureApi.Recent result = new PlatformFailureService(List.of(reader), analytics, clock).recent(TENANT, 10);
        assertThat(reader.recentLimits).hasSize(8).containsOnly(10);
        assertThat(result.items()).hasSize(10).isSortedAccordingTo((left, right) -> right.lastSeenAt().compareTo(left.lastSeenAt()));
    }

    private void assertInvalid(PlatformFailureService service, PlatformFailureApi.FailureQuery query) {
        assertThatThrownBy(() -> service.failures("BILLING", query)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeReader implements OperationalFailureReadApi {
        private final Set<Source> sources;
        private final List<Integer> recentLimits = new ArrayList<>();
        private Source unavailable;
        private FakeReader(Set<Source> sources) { this.sources = sources; }
        public Set<Source> sources() { return sources; }
        public Summary summary(Source source, Optional<UUID> tenantId) {
            if (source == unavailable) throw new IllegalStateException("owner unavailable");
            return new Summary(2, Map.of(State.FAILED, 2L), Map.of(Severity.ERROR, 2L));
        }
        public Page failures(Source source, Query query) { return new Page(List.of(), 0); }
        public List<Failure> recent(Source source, Optional<UUID> tenantId, int limit) {
            recentLimits.add(limit);
            LocalDateTime base = LocalDateTime.of(2026, 7, 28, 6, 0).minusMinutes(source.ordinal());
            return List.of(failure(source, base), failure(source, base.minusSeconds(1)));
        }
        private Failure failure(Source source, LocalDateTime seen) {
            return new Failure(source, UUID.randomUUID(), TENANT, null, ResourceType.JOB, State.FAILED,
                    Severity.ERROR, Code.INTERVIEW_TRANSPORT_FAILED, 1, seen, seen, null);
        }
    }
}
