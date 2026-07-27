package com.cacanode.api.recruitment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfficialTwilioCallTransportTest {
    @Test
    void accountAuthTokenIsADevelopmentOnlyFallback() {
        assertTrue(OfficialTwilioCallTransport.useAccountAuthToken("development"));
        assertTrue(OfficialTwilioCallTransport.useAccountAuthToken("DEVELOPMENT"));
        assertFalse(OfficialTwilioCallTransport.useAccountAuthToken("staging"));
        assertFalse(OfficialTwilioCallTransport.useAccountAuthToken("production"));
    }

    @Test
    void providerCallLengthFollowsTheBoundAccountCapabilityFlag() {
        assertEquals(600,OfficialTwilioCallTransport.providerTimeLimitSeconds(1500,false));
        assertEquals(420,OfficialTwilioCallTransport.providerTimeLimitSeconds(300,false));
        assertEquals(1620,OfficialTwilioCallTransport.providerTimeLimitSeconds(1500,true));
        assertEquals(14400,OfficialTwilioCallTransport.providerTimeLimitSeconds(14400,true));
    }
}
