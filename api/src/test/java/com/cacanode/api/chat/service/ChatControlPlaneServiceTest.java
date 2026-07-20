package com.cacanode.api.chat.service;

import com.cacanode.api.chat.enums.ChatChannel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControlPlaneServiceTest {

    @Test
    void ticketDraftResponsesSuppressCitationMarkers() {
        assertTrue(ChatControlPlaneService.isTicketDraft(Map.of("type", "ticket_draft")));
        assertTrue(ChatControlPlaneService.isTicketDraft(Map.of("type", "CREATE_TICKET_DRAFT")));
        assertEquals(
                "A support ticket draft is ready for review.",
                ChatControlPlaneService.withoutCitationMarkers(
                        "A support ticket draft is ready for review. [S1] [S2]"));
    }

    @Test
    void ordinaryKnowledgeAnswersAreNotClassifiedAsTicketDrafts() {
        assertFalse(ChatControlPlaneService.isTicketDraft(Map.of()));
        assertFalse(ChatControlPlaneService.isTicketDraft(null));
    }

    @Test
    void widgetAndCustomApiSessionsReceivePublicEvidenceLinks() {
        assertTrue(ChatControlPlaneService.supportsPublicEvidence(ChatChannel.WIDGET));
        assertTrue(ChatControlPlaneService.supportsPublicEvidence(ChatChannel.CUSTOM_API));
        assertFalse(ChatControlPlaneService.supportsPublicEvidence(ChatChannel.EMPLOYEE_PLAYGROUND));
    }
}
