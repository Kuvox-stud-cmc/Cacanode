package com.cacanode.api.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailServiceTest {

    private EmailProvider sendGridProvider;
    private EmailProvider brevoProvider;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        sendGridProvider = mock(EmailProvider.class);
        brevoProvider = mock(EmailProvider.class);

        when(sendGridProvider.providerName()).thenReturn("SendGrid");
        when(brevoProvider.providerName()).thenReturn("Brevo");

        emailService = new EmailService(
                sendGridProvider,
                brevoProvider,
                "http://localhost:3000/verify-email",
                "http://localhost:3000/verify-login"
        );
    }

    @Test
    void sendGridSuccessDoesNotCallBrevo() {
        emailService.sendWelcomeEmail("user@example.com", "Ada Lovelace", "Example Co", "verify-token");

        ArgumentCaptor<EmailMessage> messageCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sendGridProvider).send(messageCaptor.capture());
        verify(brevoProvider, never()).send(any());

        EmailMessage message = messageCaptor.getValue();
        assertEquals("user@example.com", message.toEmail());
        assertEquals("Ada Lovelace", message.toName());
        assertEquals("Welcome to CacaNode - Confirm your email", message.subject());
    }

    @Test
    void sendGridFailureThenBrevoSuccessDoesNotThrow() {
        doThrow(new EmailDeliveryException("sendgrid down"))
                .when(sendGridProvider)
                .send(any(EmailMessage.class));

        emailService.sendLogin2FAEmail("user@example.com", "Ada Lovelace", "login-token");

        verify(sendGridProvider).send(any(EmailMessage.class));
        verify(brevoProvider).send(any(EmailMessage.class));
    }

    @Test
    void bothProvidersFailThrowsDeliveryException() {
        doThrow(new EmailDeliveryException("sendgrid down"))
                .when(sendGridProvider)
                .send(any(EmailMessage.class));
        doThrow(new EmailDeliveryException("brevo down"))
                .when(brevoProvider)
                .send(any(EmailMessage.class));

        assertThrows(
                EmailDeliveryException.class,
                () -> emailService.sendWelcomeEmail("user@example.com", "Ada Lovelace", "Example Co", "verify-token")
        );
    }
}
