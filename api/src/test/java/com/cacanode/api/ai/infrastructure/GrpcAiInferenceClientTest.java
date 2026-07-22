package com.cacanode.api.ai.infrastructure;

import com.cacanode.ai.v1.GenerateAnswerResponse;
import com.cacanode.ai.v1.TicketDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GrpcAiInferenceClientTest {

    @Test
    void mapsTicketDraftToPublicActionContract() {
        GenerateAnswerResponse response = GenerateAnswerResponse.newBuilder()
                .setTicketDraft(TicketDraft.newBuilder()
                        .setTitle("Duplicate payment")
                        .setDescription("Customer was charged twice.")
                        .setCustomerEmail("customer@example.com")
                        .putMetadata("priority", "urgent"))
                .build();

        var action = GrpcAiInferenceClient.ticketDraftAction(response);

        assertEquals("ticket_draft", action.get("type"));
        assertEquals("Duplicate payment", action.get("title"));
        assertEquals("Customer was charged twice.", action.get("description"));
        assertEquals("customer@example.com", action.get("customer_email"));
        assertEquals("urgent", ((java.util.Map<?, ?>) action.get("metadata")).get("priority"));
    }

    @Test
    void returnsNoActionWithoutTicketDraft() {
        assertNull(GrpcAiInferenceClient.ticketDraftAction(
                GenerateAnswerResponse.getDefaultInstance()));
    }
}
