package com.cacanode.api.tenant.cache;

import com.cacanode.api.common.cache.CacheOperationStatus;
import com.cacanode.api.common.cache.CacheStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class IntegrationTokenCacheInvalidationListenerTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private CacheStore cacheStore;

    @Test
    void deletesExactVersionedKeysSynchronouslyAfterCommit() {
        reset(cacheStore);
        when(cacheStore.delete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(CacheOperationStatus.SUCCESS);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new IntegrationTokenCacheInvalidationEvent(
                    Set.of("hash-a", "hash-b")
            ));
            verifyNoInteractions(cacheStore);
        });

        verify(cacheStore).delete("integration-token", "ccn:v1:integration-token:hash-a");
        verify(cacheStore).delete("integration-token", "ccn:v1:integration-token:hash-b");
    }

    @Test
    void rollbackDoesNotDeleteAnything() {
        reset(cacheStore);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new IntegrationTokenCacheInvalidationEvent(Set.of("hash-a")));
            status.setRollbackOnly();
        });

        verifyNoInteractions(cacheStore);
    }
}
