package com.cacanode.api.common.cache;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class BusinessCacheInvalidationPublisher {
    private final ApplicationEventPublisher publisher;

    public BusinessCacheInvalidationPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void widget(UUID tenantId) {
        fixed(tenantId, BusinessCache.WIDGET_CONFIG);
    }

    public void prompt(UUID tenantId) {
        fixed(tenantId, BusinessCache.CUSTOMER_ANSWER_PROMPT);
    }

    public void billing(UUID tenantId) {
        fixed(tenantId, BusinessCache.BILLING_ACCOUNT);
    }

    public void workspace(UUID tenantId) {
        fixed(tenantId, BusinessCache.WORKSPACE);
    }

    public void memberMutation(UUID tenantId) {
        publisher.publishEvent(new BusinessCacheInvalidationEvent(tenantId, Set.of(
                BusinessCache.BILLING_ACCOUNT, BusinessCache.DASHBOARD, BusinessCache.USER_DIRECTORY)));
    }

    public void documentMutation(UUID tenantId, UUID knowledgeBaseId) {
        publisher.publishEvent(new BusinessCacheInvalidationEvent(tenantId, Set.of(
                BusinessCache.BILLING_ACCOUNT, BusinessCache.DASHBOARD)));
        publisher.publishEvent(new DocumentListInvalidationEvent(tenantId, knowledgeBaseId));
    }

    public void entitlements(UUID tenantId, boolean brandingChanged) {
        publisher.publishEvent(new BusinessCacheInvalidationEvent(tenantId, brandingChanged
                ? Set.of(BusinessCache.BILLING_ACCOUNT, BusinessCache.DASHBOARD, BusinessCache.WIDGET_CONFIG)
                : Set.of(BusinessCache.BILLING_ACCOUNT, BusinessCache.DASHBOARD)));
    }

    private void fixed(UUID tenantId, BusinessCache cache) {
        publisher.publishEvent(new BusinessCacheInvalidationEvent(tenantId, Set.of(cache)));
    }
}
