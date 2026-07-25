package com.cacanode.api.recruitment.listener;

import com.cacanode.api.recruitment.repository.RecruitmentRecordingOperationWorker;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RecruitmentRecordingOperationListenerTest {
    private final RecruitmentRecordingOperationWorker worker = mock(RecruitmentRecordingOperationWorker.class);
    private final RecruitmentRecordingOperationListener listener =
            new RecruitmentRecordingOperationListener(worker);

    @Test
    void dispatchesCanonicalOperationId() {
        UUID operationId = UUID.fromString("350bcaa8-4817-5f23-9fb9-458b99e6b2e8");

        listener.receive(operationId.toString().getBytes(StandardCharsets.UTF_8));

        verify(worker).process(operationId);
    }

    @Test
    void rejectsMalformedNotificationForListenerRetryAndDeadLettering() {
        assertThrows(IllegalArgumentException.class,
                () -> listener.receive("not-an-operation".getBytes(StandardCharsets.UTF_8)));
        verify(worker, never()).process(org.mockito.ArgumentMatchers.any());
    }
}
