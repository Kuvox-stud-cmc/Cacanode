package com.cacanode.api.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerAnswerPromptDefaultsTest {
    @Test
    void defaultIsPersonalizedAndCoversConversationalEdgeCases() {
        String acme = CustomerAnswerPromptDefaults.forTenant("  Acme   Corporation  ");
        String globex = CustomerAnswerPromptDefaults.forTenant("Globex");

        assertTrue(acme.contains("Acme Corporation"));
        assertTrue(acme.contains("Respond to every customer message politely"));
        assertTrue(acme.contains("greetings, thanks, farewells"));
        assertTrue(acme.contains("without requiring a citation"));
        assertTrue(acme.contains("instead of guessing"));
        assertNotEquals(acme, globex);
    }
}
