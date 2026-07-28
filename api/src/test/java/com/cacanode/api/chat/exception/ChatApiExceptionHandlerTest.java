package com.cacanode.api.chat.exception;

import com.cacanode.api.billing.api.MessageQuotaExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ChatApiExceptionHandlerTest {
    @Test
    void messageQuotaExceededMapsTo429WithoutChangingTheErrorEnvelope() {
        var response = new ChatApiExceptionHandler().handleQuota(
                new MessageQuotaExceededException(), new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error").toString())
                .contains("MESSAGE_QUOTA_EXCEEDED", "request_id");
    }
}
