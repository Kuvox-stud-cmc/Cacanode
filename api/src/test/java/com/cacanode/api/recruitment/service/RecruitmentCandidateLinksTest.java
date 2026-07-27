package com.cacanode.api.recruitment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecruitmentCandidateLinksTest {
    @Test void usesEmailClientSafeQueryParametersAndPreservesExistingQueries() {
        assertEquals("https://cacanode.com/applications/manage?token=abc_123",
                RecruitmentCandidateLinks.withToken("https://cacanode.com/applications/manage","token","abc_123"));
        assertEquals("https://cacanode.com/applications/manage?source=email&invitation=invite-token",
                RecruitmentCandidateLinks.withToken("https://cacanode.com/applications/manage?source=email","invitation","invite-token"));
    }
}
