package com.cacanode.api.platform;

import com.cacanode.api.common.api.operations.OperationalFailureReadApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalFailureSerializationTest {
    @Test
    void normalizedFailureCannotSerializeSensitiveOwnerFields() throws Exception {
        var value = new OperationalFailureReadApi.Failure(OperationalFailureReadApi.Source.WEBHOOKS,
                UUID.randomUUID(), UUID.randomUUID(), null, OperationalFailureReadApi.ResourceType.WEBHOOK_EVENT,
                OperationalFailureReadApi.State.RETRYING, OperationalFailureReadApi.Severity.WARNING,
                OperationalFailureReadApi.Code.WEBHOOK_DELIVERY_RETRY, 2,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now().plusMinutes(1));
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(value).toLowerCase();
        assertThat(json).doesNotContain("payload", "rawerror", "failure_reason", "provider", "recipient",
                "filename", "prompt", "token", "url", "candidate", "application", "cv_id");
    }
}
