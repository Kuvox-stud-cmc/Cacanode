package com.cacanode.api.bootstrap;

import com.cacanode.api.analytics.api.AnalyticsProjectionRebuildApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.analytics.rebuild-on-startup", havingValue = "true")
public class AnalyticsProjectionRebuildRunner implements ApplicationRunner {
    private final AnalyticsProjectionRebuildApi rebuildApi;

    @Override
    public void run(ApplicationArguments args) {
        var result = rebuildApi.rebuild();
        log.info("Analytics projections rebuilt: {}", result);
    }
}
