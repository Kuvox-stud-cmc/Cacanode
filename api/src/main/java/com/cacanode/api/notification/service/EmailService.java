package com.cacanode.api.notification.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "EMAIL-SERVICE")
@Service
public class EmailService {

    private final EmailProvider primaryProvider;
    private final EmailProvider fallbackProvider;
    private final String verificationLink;
    private final String login2FALink;

    public EmailService(
            @Qualifier("sendGridEmailProvider") EmailProvider primaryProvider,
            @Qualifier("brevoEmailProvider") EmailProvider fallbackProvider,
            @Value("${app.email.verification-link}") String verificationLink,
            @Value("${app.email.login-2fa-link:http://localhost:3000/verify-login}") String login2FALink) {
        this.primaryProvider = primaryProvider;
        this.fallbackProvider = fallbackProvider;
        this.verificationLink = verificationLink;
        this.login2FALink = login2FALink;
    }

    public void sendWelcomeEmail(String toEmail, String fullName, String companyName, String verificationToken) {
        EmailMessage message = new EmailMessage(
                toEmail,
                fullName,
                "Welcome to CacaNode - Confirm your email",
                buildWelcomeEmailHtml(fullName, companyName, verificationToken)
        );
        sendWithFallback(message);
    }

    private String buildWelcomeEmailHtml(
            String fullName,
            String companyName,
            String verificationToken) {
        String verifyUrl = verificationLink + "?token=" + verificationToken;

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; background: #f9f9f9; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 40px auto; background: #fff;
                                     border-radius: 8px; padding: 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
                        .logo { font-size: 24px; font-weight: bold; color: #4f46e5; margin-bottom: 24px; }
                        h1 { font-size: 22px; color: #111827; }
                        p { color: #6b7280; line-height: 1.6; }
                        .btn { display: inline-block; padding: 12px 28px; background: #4f46e5;
                               color: #fff!important; text-decoration: none; border-radius: 6px;
                               font-weight: bold; margin: 24px 0; }
                        .footer { margin-top: 32px; font-size: 12px; color: #9ca3af; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="logo">CacaNode</div>
                        <h1>Welcome, %s!</h1>
                        <p>
                            Thank you for registering <strong>%s</strong> on CacaNode.
                            Your AI-powered chatbot platform is ready to set up.
                        </p>
                        <p>Please confirm your email address to activate your account:</p>
                        <a href="%s" class="btn">Confirm Email</a>
                        <p>If you did not create this account, you can safely ignore this email.</p>
                        <div class="footer">
                            © 2026 CacaNode. All rights reserved.<br>
                            This link expires in 24 hours.
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(fullName, companyName, verifyUrl);
    }

    public void sendLogin2FAEmail(String toEmail, String fullName, String verificationToken) {
        EmailMessage message = new EmailMessage(
                toEmail,
                fullName,
                "Login Verification - CacaNode",
                buildLogin2FAEmailHtml(fullName, verificationToken)
        );
        sendWithFallback(message);
    }

    private void sendWithFallback(EmailMessage message) {
        try {
            primaryProvider.send(message);
            log.info("Email sent to {} via {}", message.toEmail(), primaryProvider.providerName());
            return;
        } catch (EmailDeliveryException primaryFailure) {
            log.warn(
                    "{} failed to send email to {}. Trying {}. Reason: {}",
                    primaryProvider.providerName(),
                    message.toEmail(),
                    fallbackProvider.providerName(),
                    primaryFailure.getMessage()
            );

            try {
                fallbackProvider.send(message);
                log.info("Email sent to {} via {}", message.toEmail(), fallbackProvider.providerName());
            } catch (EmailDeliveryException fallbackFailure) {
                EmailDeliveryException deliveryFailure = new EmailDeliveryException(
                        "Email delivery failed with %s and %s".formatted(
                                primaryProvider.providerName(),
                                fallbackProvider.providerName()
                        ),
                        fallbackFailure
                );
                deliveryFailure.addSuppressed(primaryFailure);
                throw deliveryFailure;
            }
        }
    }

    private String buildLogin2FAEmailHtml(String fullName, String verificationToken) {
        String verifyUrl = login2FALink + "?token=" + verificationToken;

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; background: #f9f9f9; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 40px auto; background: #fff;
                                     border-radius: 8px; padding: 40px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
                        .logo { font-size: 24px; font-weight: bold; color: #4f46e5; margin-bottom: 24px; }
                        h1 { font-size: 22px; color: #111827; }
                        p { color: #6b7280; line-height: 1.6; }
                        .btn { display: inline-block; padding: 12px 28px; background: #4f46e5;
                               color: #fff!important; text-decoration: none; border-radius: 6px;
                               font-weight: bold; margin: 24px 0; }
                        .footer { margin-top: 32px; font-size: 12px; color: #9ca3af; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="logo">CacaNode</div>
                        <h1>Hello, %s!</h1>
                        <p>
                            We received a login request for your CacaNode account.
                            Please click the button below to verify and complete your login.
                        </p>
                        <a href="%s" class="btn">Verify Login</a>
                        <p>If you did not attempt to log in, please ignore this email and ensure your account is secure.</p>
                        <div class="footer">
                            © 2026 CacaNode. All rights reserved.<br>
                            This link expires in 15 minutes.
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(fullName, verifyUrl);
    }
}
