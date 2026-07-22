package com.cacanode.api.chat.service;

import com.cacanode.api.ai.api.AiInferenceApi;
import com.cacanode.api.billing.api.BillingQuotaApi;
import com.cacanode.api.chat.enums.ChatTurnStatus;
import com.cacanode.api.chat.model.ChatTurn;
import com.cacanode.api.chat.query.ChatControlPlaneService;
import com.cacanode.api.chat.repository.ChatMessageRepository;
import com.cacanode.api.chat.repository.ChatSessionRepository;
import com.cacanode.api.chat.repository.ChatTurnRepository;
import com.cacanode.api.document.api.DocumentApi;
import com.cacanode.api.tenant.api.TenantWorkspaceApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatQuotaRollbackTest {
    @Test
    void failedTurnRollsBackItsQuotaConsumptionOnlyOnce() {
        ChatTurnRepository turns = mock(ChatTurnRepository.class);
        BillingQuotaApi quota = mock(BillingQuotaApi.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        UUID tenantId = UUID.randomUUID();
        UUID consumptionId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        ChatTurn turn = new ChatTurn();
        turn.setStatus(ChatTurnStatus.PENDING);
        turn.setTenantId(tenantId);
        turn.setQuotaConsumptionId(consumptionId);
        when(turns.findById(turnId)).thenReturn(Optional.of(turn));
        ChatControlPlaneService service = new ChatControlPlaneService(
                mock(ChatSessionRepository.class), mock(ChatMessageRepository.class), turns,
                mock(TenantWorkspaceApi.class), mock(DocumentApi.class), quota,
                mock(ApplicationEventPublisher.class), mock(AiInferenceApi.class),
                new ObjectMapper(), transactions);

        ReflectionTestUtils.invokeMethod(service, "markFailed", turnId, "AI_FAILURE");
        ReflectionTestUtils.invokeMethod(service, "markFailed", turnId, "AI_FAILURE");

        verify(quota, times(1)).rollbackMessageQuota(tenantId, consumptionId);
    }
}
