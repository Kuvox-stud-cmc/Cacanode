package com.cacanode.api.recruitment.controller;

import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.cacanode.api.recruitment.model.RecruitmentInterviewCallAttempt;
import com.cacanode.api.recruitment.service.TwilioCallbackService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TwilioInterviewCallbackControllerTest {
    @Test
    void formBodyRemainsAvailableWhenAttemptIsInTheQueryString() throws Exception {
        TwilioCallbackService callbacks=mock(TwilioCallbackService.class);
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        UUID attempt=UUID.randomUUID();

        standaloneSetup(new TwilioInterviewCallbackController(callbacks,properties)).build().perform(post(
                        "/api/v1/public/twilio/interviews/status?attempt="+attempt)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("CallSid=CA"+"1".repeat(32)+"&CallStatus=completed&SequenceNumber=1"))
                .andExpect(status().isNoContent());

        verify(callbacks).validate(any(),argThat(form->"completed".equals(form.getFirst("CallStatus"))
                &&!form.containsKey("attempt")));
        verify(callbacks).status(any(),argThat(form->"1".equals(form.getFirst("SequenceNumber"))));
    }

    @Test
    void englishConsentListensForDtmfAndShortSpeechOnEveryPrompt() throws Exception {
        TwilioCallbackService callbacks=mock(TwilioCallbackService.class);
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        when(callbacks.disclosure(any())).thenReturn("Press 1 to consent or 2 to decline.");
        when(callbacks.languageTag(any())).thenReturn("en-US");
        UUID attempt=UUID.randomUUID();

        String twiml=standaloneSetup(new TwilioInterviewCallbackController(callbacks,properties)).build().perform(post(
                        "/api/v1/public/twilio/interviews/voice?attempt="+attempt)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("CallSid=CA"+"1".repeat(32)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(twiml.contains("voice=\"alice\""));
        assertTrue(twiml.contains("language=\"en-US\""));
        assertTrue(twiml.contains("input=\"dtmf speech\""));
        assertTrue(twiml.contains("numDigits=\"1\""));
        assertTrue(twiml.contains("timeout=\"6\""));
        assertTrue(twiml.contains("speechTimeout=\"1\""));
        assertTrue(twiml.contains("speechModel=\"experimental_utterances\""));
        assertTrue(twiml.contains("Press 1"));
        assertTrue(twiml.contains("say yes"));
    }

    @Test
    void spokenYesAcceptsConsentWhenForwardingDropsDtmf() throws Exception {
        TwilioCallbackService callbacks=mock(TwilioCallbackService.class);
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        RecruitmentInterviewCallAttempt call=mock(RecruitmentInterviewCallAttempt.class);
        when(callbacks.bind(eq(attemptId()),any(),any(),any())).thenReturn(call);
        when(callbacks.runtimeToken(call)).thenReturn("runtime-token");
        UUID attempt=attemptId();

        String twiml=standaloneSetup(new TwilioInterviewCallbackController(callbacks,properties)).build().perform(post(
                        "/api/v1/public/twilio/interviews/consent?attempt="+attempt+"&round=1")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("Digits=&SpeechResult=yes"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        verify(callbacks).consent(eq(call),eq(true),isNull());
        assertTrue(twiml.contains("<Connect><Stream"));
    }

    @Test
    void repeatedNaturalSpokenYesAcceptsConsent() throws Exception {
        TwilioCallbackService callbacks=mock(TwilioCallbackService.class);
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        RecruitmentInterviewCallAttempt call=mock(RecruitmentInterviewCallAttempt.class);
        when(callbacks.bind(eq(attemptId()),any(),any(),any())).thenReturn(call);
        when(callbacks.runtimeToken(call)).thenReturn("runtime-token");

        String twiml=standaloneSetup(new TwilioInterviewCallbackController(callbacks,properties)).build().perform(post(
                        "/api/v1/public/twilio/interviews/consent?attempt="+attemptId()+"&round=2")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("SpeechResult=Yes%2C+come+on.+Yes%2C+yes."))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        verify(callbacks).consent(eq(call),eq(true),isNull());
        assertTrue(twiml.contains("<Connect><Stream"));
    }

    @Test
    void explicitNegativeSpeechWinsOverAConsentWord() throws Exception {
        TwilioCallbackService callbacks=mock(TwilioCallbackService.class);
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        RecruitmentInterviewCallAttempt call=mock(RecruitmentInterviewCallAttempt.class);
        when(callbacks.bind(eq(attemptId()),any(),any(),any())).thenReturn(call);

        String twiml=standaloneSetup(new TwilioInterviewCallbackController(callbacks,properties)).build().perform(post(
                        "/api/v1/public/twilio/interviews/consent?attempt="+attemptId()+"&round=2")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("SpeechResult=No%2C+I+do+not+consent."))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        verify(callbacks).consent(call,false,"CONSENT_DECLINED");
        assertTrue(twiml.contains("<Hangup/>"));
    }

    @Test
    void repeatedDigitOneAcceptsConsentAndStartsTheMediaStream() throws Exception {
        TwilioCallbackService callbacks=mock(TwilioCallbackService.class);
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        RecruitmentInterviewCallAttempt call=mock(RecruitmentInterviewCallAttempt.class);
        when(callbacks.bind(eq(attemptId()),any(),any(),any())).thenReturn(call);
        when(callbacks.runtimeToken(call)).thenReturn("runtime-token");

        String twiml=standaloneSetup(new TwilioInterviewCallbackController(callbacks,properties)).build().perform(post(
                        "/api/v1/public/twilio/interviews/consent?attempt="+attemptId()+"&round=1")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("CallSid=CA"+"1".repeat(32)+"&Digits=1111"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        verify(callbacks).consent(eq(call),eq(true),isNull());
        assertTrue(twiml.contains("<Connect><Stream"));
    }

    @Test
    void emptyConsentRoundKeepsBothInputMethodsEnabled() throws Exception {
        TwilioCallbackService callbacks=mock(TwilioCallbackService.class);
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        RecruitmentInterviewCallAttempt call=mock(RecruitmentInterviewCallAttempt.class);
        when(callbacks.bind(eq(attemptId()),any(),any(),any())).thenReturn(call);
        when(callbacks.languageTag(call)).thenReturn("vi-VN");

        String twiml=standaloneSetup(new TwilioInterviewCallbackController(callbacks,properties)).build().perform(post(
                        "/api/v1/public/twilio/interviews/consent?attempt="+attemptId()+"&round=1")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("Digits=&SpeechResult="))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        verify(callbacks,never()).consent(any(),eq(true),any());
        assertTrue(twiml.contains("round=2"));
        assertTrue(twiml.contains("language=\"vi-VN\""));
        assertTrue(twiml.contains("input=\"dtmf speech\""));
        assertTrue(twiml.contains("numDigits=\"1\""));
        assertTrue(twiml.contains("speechTimeout=\"1\""));
        assertTrue(twiml.contains("speechModel=\"experimental_utterances\""));
    }

    @Test
    void laterRetryStillAcceptsBothDtmfAndSpeech() throws Exception {
        TwilioCallbackService callbacks=mock(TwilioCallbackService.class);
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        RecruitmentInterviewCallAttempt call=mock(RecruitmentInterviewCallAttempt.class);
        when(callbacks.bind(eq(attemptId()),any(),any(),any())).thenReturn(call);
        when(callbacks.languageTag(call)).thenReturn("en-US");

        String twiml=standaloneSetup(new TwilioInterviewCallbackController(callbacks,properties)).build().perform(post(
                        "/api/v1/public/twilio/interviews/consent?attempt="+attemptId()+"&round=2")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("SpeechResult="))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        verify(callbacks,never()).consent(any(),eq(false),any());
        assertTrue(twiml.contains("round=3"));
        assertTrue(twiml.contains("input=\"dtmf speech\""));
        assertTrue(twiml.contains("numDigits=\"1\""));
        assertTrue(twiml.contains("speechTimeout=\"1\""));
        assertTrue(twiml.contains("say yes"));
    }

    @Test
    void finalRetryStillAcceptsBothInputMethodsBeforeTimingOut() throws Exception {
        TwilioCallbackService callbacks=mock(TwilioCallbackService.class);
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        RecruitmentInterviewCallAttempt call=mock(RecruitmentInterviewCallAttempt.class);
        when(callbacks.bind(eq(attemptId()),any(),any(),any())).thenReturn(call);
        when(callbacks.languageTag(call)).thenReturn("en-US");

        String twiml=standaloneSetup(new TwilioInterviewCallbackController(callbacks,properties)).build().perform(post(
                        "/api/v1/public/twilio/interviews/consent?attempt="+attemptId()+"&round=3")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("Digits="))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(twiml.contains("round=4"));
        assertTrue(twiml.contains("input=\"dtmf speech\""));
        assertTrue(twiml.contains("Please answer now. Press 1 or say yes"));
    }

    @Test
    void fourthEmptyRoundEndsWithoutConsent() throws Exception {
        TwilioCallbackService callbacks=mock(TwilioCallbackService.class);
        RecruitmentCallingProperties properties=new RecruitmentCallingProperties(false,false,15,120,2,2,10,2,
                "","","","","","","","","","","","","",false,false,false,"test",false);
        RecruitmentInterviewCallAttempt call=mock(RecruitmentInterviewCallAttempt.class);
        when(callbacks.bind(eq(attemptId()),any(),any(),any())).thenReturn(call);

        String twiml=standaloneSetup(new TwilioInterviewCallbackController(callbacks,properties)).build().perform(post(
                        "/api/v1/public/twilio/interviews/consent?attempt="+attemptId()+"&round=4")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("SpeechResult="))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        verify(callbacks).consent(call,false,"CONSENT_NOT_RECEIVED");
        assertTrue(twiml.contains("<Hangup/>"));
    }

    private static UUID attemptId(){return UUID.fromString("00000000-0000-0000-0000-000000000123");}
}
