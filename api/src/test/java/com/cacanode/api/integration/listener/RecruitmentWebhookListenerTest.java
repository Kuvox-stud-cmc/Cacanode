package com.cacanode.api.integration.listener;

import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import com.cacanode.api.integration.service.WebhookService;
import com.cacanode.api.recruitment.api.event.RecordingReadyEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecruitmentWebhookListenerTest {
    @Test void recordingReadyPayloadExcludesProviderAndStorageData() {
        WebhookService webhooks=mock(WebhookService.class);ModuleEventInboxService inbox=mock(ModuleEventInboxService.class);
        when(inbox.claim(any())).thenReturn(true);var listener=new RecruitmentWebhookListener(webhooks,inbox);
        UUID tenant=UUID.randomUUID(),session=UUID.randomUUID(),attempt=UUID.randomUUID();
        listener.recording(new RecordingReadyEvent("1.0",UUID.randomUUID(),"recruitment.recording.ready",
                Instant.now(),tenant,session,session,attempt,"private/storage/key","audio/mpeg",1234,
                "a".repeat(64),Instant.now().plusSeconds(3600)));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> payload=ArgumentCaptor.forClass(Map.class);
        verify(webhooks).enqueue(eq(tenant),eq("recording.ready"),eq(session),payload.capture());
        assertFalse(payload.getValue().keySet().stream().anyMatch(Set.of(
                "storageKey","contentType","sizeBytes","sha256","provider","providerId")::contains));
    }
}
