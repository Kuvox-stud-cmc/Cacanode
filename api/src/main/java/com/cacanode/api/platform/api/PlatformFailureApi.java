package com.cacanode.api.platform.api;

import com.cacanode.api.common.api.operations.OperationalFailureReadApi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PlatformFailureApi {
    Summary summary(UUID tenantId);
    FailurePage failures(String source, FailureQuery query);
    Recent recent(UUID tenantId, int limit);

    record FailureQuery(UUID tenantId, String state, String severity, int page, int size,
                        String sort, String direction) {}
    record Warning(String code, OperationalFailureReadApi.Source source) {}
    record SourceSummary(OperationalFailureReadApi.Source source, long total,
                         Map<OperationalFailureReadApi.State, Long> states,
                         Map<OperationalFailureReadApi.Severity, Long> severities) {}
    record Summary(LocalDateTime generatedAt, List<SourceSummary> sources, boolean partial,
                   List<Warning> warnings) {
        public Summary { sources = List.copyOf(sources); warnings = List.copyOf(warnings); }
    }
    record FailurePage(LocalDateTime generatedAt, OperationalFailureReadApi.Source source,
                       List<OperationalFailureReadApi.Failure> items, int page, int size, long total,
                       boolean partial, List<Warning> warnings) {
        public FailurePage { items = List.copyOf(items); warnings = List.copyOf(warnings); }
    }
    record Recent(List<OperationalFailureReadApi.Failure> items, boolean partial, List<Warning> warnings) {
        public Recent { items = List.copyOf(items); warnings = List.copyOf(warnings); }
    }
}
