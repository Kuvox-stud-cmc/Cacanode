package com.cacanode.api.notification.service;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "EMAIL-SERVICE")
@Service
@RequiredArgsConstructor
public class EmailService {

  private final SendGrid sendGrid;

  @Value("${spring.sendgrid.from-email}")
  private String fromEmail;

  @Value("${spring.sendgrid.verification-link}")
  private String verificationLink;

  public void sendWelcomeEmail(String toEmail, String fullName, String companyName) {
        Email from = new Email(fromEmail, "CacaNode");
        Email to = new Email(toEmail);

        String subject = "Welcome to CacaNode — Confirm your email";
        String htmlContent = buildWelcomeEmailHtml(fullName, companyName, toEmail);

        Content content = new Content("text/html", htmlContent);
        Mail mail = new Mail(from, subject, to, content);

        try {
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("Welcome email sent to: {}", toEmail);
            } else {
                log.error("Failed to send welcome email to: {} — status: {} body: {}",
                        toEmail, response.getStatusCode(), response.getBody());
            }

        } catch (IOException e) {
            log.error("Exception sending welcome email to: {} — {}", toEmail, e.getMessage());
        }
    }

    private String buildWelcomeEmailHtml(
            String fullName,
            String companyName,
            String email
    ) {
        String verifyUrl = verificationLink + "?email=" + email;

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
                               color: #fff; text-decoration: none; border-radius: 6px;
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
}
