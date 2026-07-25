package com.cacanode.api.recruitment.listener;

import com.cacanode.api.recruitment.api.event.PublicJobProjectionChangedEvent;
import com.cacanode.api.recruitment.query.PublicJobProjectionService;
import com.cacanode.api.tenant.api.event.TenantProjectionChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class PublicRecruitmentProjectionListener {
    private final PublicJobProjectionService projections;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onJobChanged(PublicJobProjectionChangedEvent event) {
        if (event.visible()) projections.synchronize(event.tenantId(), event.jobId());
        else projections.remove(event.tenantId(), event.jobId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTenantChanged(TenantProjectionChangedEvent event) {
        projections.refreshTenant(event.tenantId());
    }
}
