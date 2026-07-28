package com.cacanode.api.platform.service;

import com.cacanode.api.analytics.api.PlatformAnalyticsReadApi;
import com.cacanode.api.common.api.operations.OperationalFailureReadApi;
import com.cacanode.api.platform.api.PlatformFailureApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformFailureService implements PlatformFailureApi {
    private static final Set<String> SORTS = Set.of("lastSeenAt", "firstSeenAt", "attempts", "severity", "state");
    private final List<OperationalFailureReadApi> readers;
    private final PlatformAnalyticsReadApi analytics;
    private final Clock clock;

    @Override
    public Summary summary(UUID tenantId) {
        requireTenant(tenantId);
        List<SourceSummary> sources = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        for (var source : OperationalFailureReadApi.Source.values()) {
            Optional<OperationalFailureReadApi> owner = owner(source);
            if (owner.isEmpty()) {
                sources.add(new SourceSummary(source, 0, java.util.Map.of(), java.util.Map.of()));
                warnings.add(new Warning("FAILURE_SOURCE_UNAVAILABLE", source));
                continue;
            }
            try {
                var value = owner.get().summary(source, Optional.ofNullable(tenantId));
                sources.add(new SourceSummary(source, value.total(), value.states(), value.severities()));
            } catch (RuntimeException unavailable) {
                sources.add(new SourceSummary(source, 0, java.util.Map.of(), java.util.Map.of()));
                warnings.add(new Warning("FAILURE_SOURCE_UNAVAILABLE", source));
            }
        }
        return new Summary(LocalDateTime.now(clock), sources, !warnings.isEmpty(), warnings);
    }

    @Override
    public FailurePage failures(String rawSource, FailureQuery query) {
        OperationalFailureReadApi.Source source = source(rawSource);
        requireTenant(query.tenantId());
        if (query.size() < 1 || query.size() > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        if (query.page() < 0) throw new IllegalArgumentException("page must not be negative");
        if (!SORTS.contains(query.sort())) throw new IllegalArgumentException("Unsupported failure sort");
        if (!Set.of("asc", "desc").contains(query.direction().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("direction must be asc or desc");
        OperationalFailureReadApi.State state = enumValue(OperationalFailureReadApi.State.class, query.state(), "state");
        OperationalFailureReadApi.Severity severity = enumValue(OperationalFailureReadApi.Severity.class,
                query.severity(), "severity");
        Optional<OperationalFailureReadApi> owner = owner(source);
        List<Warning> warnings = new ArrayList<>();
        OperationalFailureReadApi.Page value = new OperationalFailureReadApi.Page(List.of(), 0);
        if (owner.isEmpty()) warnings.add(new Warning("FAILURE_SOURCE_UNAVAILABLE", source));
        else try {
            value = owner.get().failures(source, new OperationalFailureReadApi.Query(
                    Optional.ofNullable(query.tenantId()), state, severity, query.page(), query.size(),
                    query.sort(), query.direction()));
        } catch (RuntimeException unavailable) {
            warnings.add(new Warning("FAILURE_SOURCE_UNAVAILABLE", source));
        }
        return new FailurePage(LocalDateTime.now(clock), source, value.items(), query.page(), query.size(),
                value.total(), !warnings.isEmpty(), warnings);
    }

    @Override
    public Recent recent(UUID tenantId, int limit) {
        requireTenant(tenantId);
        int bounded = Math.max(0, Math.min(limit, 100));
        List<OperationalFailureReadApi.Failure> values = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        for (var source : OperationalFailureReadApi.Source.values()) {
            if (source == OperationalFailureReadApi.Source.MODULE_EVENTS) continue;
            Optional<OperationalFailureReadApi> owner = owner(source);
            if (owner.isEmpty()) { warnings.add(new Warning("FAILURE_SOURCE_UNAVAILABLE", source)); continue; }
            try { values.addAll(owner.get().recent(source, Optional.of(tenantId), bounded)); }
            catch (RuntimeException unavailable) { warnings.add(new Warning("FAILURE_SOURCE_UNAVAILABLE", source)); }
        }
        values.sort(Comparator.comparing(OperationalFailureReadApi.Failure::lastSeenAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed()
                .thenComparing(OperationalFailureReadApi.Failure::failureId));
        return new Recent(values.stream().limit(bounded).toList(), !warnings.isEmpty(), warnings);
    }

    private Optional<OperationalFailureReadApi> owner(OperationalFailureReadApi.Source source) {
        return readers.stream().filter(reader -> reader.sources().contains(source)).findFirst();
    }
    private void requireTenant(UUID tenantId) {
        if (tenantId != null && analytics.tenantLabel(tenantId).isEmpty())
            throw new IllegalArgumentException("Customer tenant was not found");
    }
    private OperationalFailureReadApi.Source source(String value) {
        try { return OperationalFailureReadApi.Source.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Unknown failure source"); }
    }
    private <T extends Enum<T>> T enumValue(Class<T> type, String value, String label) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Unknown failure " + label); }
    }
}
