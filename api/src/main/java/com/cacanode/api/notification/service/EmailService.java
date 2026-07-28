package com.cacanode.api.notification.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.time.Instant;

import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "EMAIL-SERVICE")
@Service
public class EmailService {

    private final EmailProvider primaryProvider;
    private final EmailProvider fallbackProvider;
    private final String verificationLink;
    private final String login2FALink;
    private final String invitationLink;

    @Autowired
    public EmailService(
            @Qualifier("sendGridEmailProvider") EmailProvider primaryProvider,
            @Qualifier("brevoEmailProvider") EmailProvider fallbackProvider,
            @Value("${app.email.verification-link}") String verificationLink,
            @Value("${app.email.login-2fa-link:http://localhost:3000/verify-login}") String login2FALink,
            @Value("${app.email.invitation-link:http://localhost:3000/accept-invitation}") String invitationLink) {
        this.primaryProvider = primaryProvider;
        this.fallbackProvider = fallbackProvider;
        this.verificationLink = verificationLink;
        this.login2FALink = login2FALink;
        this.invitationLink = invitationLink;
    }

    EmailService(EmailProvider primaryProvider, EmailProvider fallbackProvider,
                 String verificationLink, String login2FALink) {
        this(primaryProvider, fallbackProvider, verificationLink, login2FALink,
                "http://localhost:3000/accept-invitation");
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

    public void sendLogin2FACodeEmail(String toEmail, String fullName, String confirmationCode) {
        EmailMessage message = new EmailMessage(
                toEmail,
                fullName,
                "Your CacaNode confirmation code",
                buildLogin2FACodeEmailHtml(fullName, confirmationCode)
        );
        sendWithFallback(message);
    }

    public void sendInvitationEmail(String toEmail, String tenantName, String role,
                                    String token, LocalDateTime expiresAt) {
        String inviteUrl = invitationLink + "?token=" + token;
        String roleLabel = "TENANT_ADMIN".equals(role) ? "Tenant admin" : "User";
        EmailMessage message = new EmailMessage(
                toEmail,
                toEmail,
                "You're invited to join " + tenantName + " on CacaNode",
                """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"><style>
                body { font-family: Arial, sans-serif; background:#f9f9f9; margin:0; padding:0; }
                .container { max-width:600px; margin:40px auto; background:#fff; border-radius:8px;
                  padding:40px; box-shadow:0 2px 8px rgba(0,0,0,.08); }
                .logo { font-size:24px; font-weight:bold; color:#4f46e5; margin-bottom:24px; }
                h1 { font-size:22px; color:#111827; } p { color:#6b7280; line-height:1.6; }
                .btn { display:inline-block; padding:12px 28px; background:#4f46e5; color:#fff!important;
                  text-decoration:none; border-radius:6px; font-weight:bold; margin:24px 0; }
                .footer { margin-top:32px; font-size:12px; color:#9ca3af; }
                </style></head><body><div class="container">
                <div class="logo">CacaNode</div>
                <h1>Join %s</h1>
                <p>You have been invited to join <strong>%s</strong> as a <strong>%s</strong>.</p>
                <a href="%s" class="btn">Accept invitation</a>
                <p>If you were not expecting this invitation, you can safely ignore this email.</p>
                <div class="footer">This link expires at %s (72 hours after it was sent).</div>
                </div></body></html>
                """.formatted(tenantName, tenantName, roleLabel, inviteUrl,
                        expiresAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        );
        sendWithFallback(message);
    }

    public void sendTicketCreatedEmail(
            String toEmail,
            String customerName,
            String tenantName,
            UUID ticketId,
            String title,
            String description,
            String locale
    ) {
        boolean vietnamese = locale != null && locale.toLowerCase().startsWith("vi");
        String reference = ticketId.toString().substring(0, 8).toUpperCase();
        String displayTenant = headerText(tenantName == null || tenantName.isBlank() ? "Support" : tenantName);
        String displayName = headerText(customerName == null || customerName.isBlank()
                ? (vietnamese ? "bạn" : "there") : customerName);
        String safeTenant = escapeHtml(displayTenant);
        String safeName = escapeHtml(displayName);
        String safeTitle = escapeHtml(title);
        String safeDescription = escapeHtml(description).replace("\n", "<br>");
        String subject = vietnamese
                ? "Đã nhận yêu cầu hỗ trợ #%s - %s".formatted(reference, displayTenant)
                : "Support ticket #%s received - %s".formatted(reference, displayTenant);
        String heading = vietnamese ? "Yêu cầu hỗ trợ đã được tiếp nhận" : "Your support request was received";
        String greeting = vietnamese ? "Xin chào " + safeName + "," : "Hello " + safeName + ",";
        String received = vietnamese
                ? "%s đã nhận được yêu cầu hỗ trợ của bạn.".formatted(safeTenant)
                : "%s has received your support request.".formatted(safeTenant);
        String status = vietnamese ? "Đang mở" : "Open";
        String ticketLabel = vietnamese ? "Mã ticket" : "Ticket";
        String titleLabel = vietnamese ? "Tiêu đề" : "Title";
        String descriptionLabel = vietnamese ? "Nội dung" : "Description";
        String statusLabel = vietnamese ? "Trạng thái" : "Status";
        String followUp = vietnamese
                ? "Đội ngũ hỗ trợ sẽ liên hệ với bạn qua địa chỉ email này."
                : "The support team will follow up using this email address.";
        String footer = vietnamese
                ? "Email được gửi an toàn bởi CacaNode."
                : "Delivered securely by CacaNode.";

        EmailMessage message = new EmailMessage(
                toEmail,
                customerName == null || customerName.isBlank() ? toEmail : displayName,
                subject,
                """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"><style>
                body { font-family:Arial,sans-serif; background:#f8fafc; margin:0; padding:0; }
                .container { max-width:600px; margin:40px auto; background:#fff; border-radius:10px;
                  padding:36px; box-shadow:0 2px 10px rgba(15,23,42,.08); }
                .brand { font-size:22px; font-weight:bold; color:#4f46e5; margin-bottom:24px; }
                h1 { font-size:22px; color:#0f172a; } p { color:#475569; line-height:1.6; }
                .ticket { margin:24px 0; overflow:hidden; border:1px solid #e2e8f0; border-radius:8px; }
                .row { padding:12px 16px; border-bottom:1px solid #e2e8f0; }
                .row:last-child { border-bottom:0; } .label { color:#64748b; font-size:12px;
                  font-weight:bold; letter-spacing:.04em; text-transform:uppercase; }
                .value { margin-top:5px; color:#0f172a; line-height:1.55; }
                .footer { margin-top:28px; color:#94a3b8; font-size:12px; }
                </style></head><body><div class="container">
                <div class="brand">%s</div>
                <h1>%s</h1>
                <p>%s</p><p>%s</p>
                <div class="ticket">
                  <div class="row"><div class="label">%s</div><div class="value">#%s</div></div>
                  <div class="row"><div class="label">%s</div><div class="value">%s</div></div>
                  <div class="row"><div class="label">%s</div><div class="value">%s</div></div>
                  <div class="row"><div class="label">%s</div><div class="value">%s</div></div>
                </div>
                <p>%s</p>
                <div class="footer">%s</div>
                </div></body></html>
                """.formatted(safeTenant, heading, greeting, received,
                        ticketLabel, reference, titleLabel, safeTitle,
                        descriptionLabel, safeDescription, statusLabel, status, followUp, footer)
        );
        sendWithFallback(message);
    }

    public void sendRecruitmentCandidateAccessEmail(String toEmail,String fullName,String companyName,
            String jobTitle,String locale,String accessUrl,boolean verification) {
        boolean vi=locale!=null&&locale.startsWith("vi");
        String subject=vi?(verification?"Xác nhận hồ sơ ứng tuyển":"Quản lý hồ sơ ứng tuyển")
                :(verification?"Verify your job application":"Manage your job application");
        String action=vi?(verification?"Xác nhận hồ sơ":"Mở hồ sơ"):(verification?"Verify application":"Open application");
        String intro=vi?"Bạn đã ứng tuyển vị trí <strong>%s</strong> tại <strong>%s</strong>."
                .formatted(escapeHtml(jobTitle),escapeHtml(companyName))
                :"You applied for <strong>%s</strong> at <strong>%s</strong>."
                .formatted(escapeHtml(jobTitle),escapeHtml(companyName));
        EmailMessage message=new EmailMessage(toEmail,fullName,subject,"""
                <!doctype html><html><body style="font-family:Arial,sans-serif;background:#f8fafc;padding:32px">
                <div style="max-width:600px;margin:auto;background:white;padding:36px;border-radius:10px">
                <h1 style="color:#0f172a;font-size:22px">%s</h1><p style="color:#475569;line-height:1.6">%s</p>
                <a href="%s" style="display:inline-block;background:#4f46e5;color:white;padding:12px 22px;border-radius:6px;text-decoration:none">%s</a>
                <p style="color:#94a3b8;font-size:12px;margin-top:28px">CacaNode recruitment</p>
                </div></body></html>
                """.formatted(escapeHtml(fullName),intro,escapeHtml(accessUrl),action));
        sendWithFallback(message);
    }

    public void sendRecruitmentPrivacyDeletionConfirmation(String toEmail,String fullName,String companyName,
            String jobTitle,String locale,String confirmationUrl) {
        boolean vi=locale!=null&&locale.startsWith("vi");
        String subject=vi?"Xác nhận xóa dữ liệu ứng tuyển":"Confirm application data deletion";
        String intro=vi?"Bạn đã yêu cầu xóa vĩnh viễn dữ liệu hồ sơ <strong>%s</strong> tại <strong>%s</strong>. Liên kết hết hạn sau một giờ."
                .formatted(escapeHtml(jobTitle),escapeHtml(companyName))
                :"You requested permanent deletion of your <strong>%s</strong> application at <strong>%s</strong>. This link expires in one hour."
                .formatted(escapeHtml(jobTitle),escapeHtml(companyName));
        String action=vi?"Xác nhận xóa dữ liệu":"Confirm deletion";
        EmailMessage message=new EmailMessage(toEmail,fullName,subject,"""
                <!doctype html><html><body style="font-family:Arial,sans-serif;background:#f8fafc;padding:32px">
                <div style="max-width:600px;margin:auto;background:white;padding:36px;border-radius:10px">
                <h1 style="color:#0f172a;font-size:22px">%s</h1><p style="color:#475569;line-height:1.6">%s</p>
                <a href="%s" style="display:inline-block;background:#b91c1c;color:white;padding:12px 22px;border-radius:6px;text-decoration:none">%s</a>
                </div></body></html>
                """.formatted(escapeHtml(fullName),intro,escapeHtml(confirmationUrl),action));
        sendWithFallback(message);
    }

    public void sendRecruitmentInterviewEmail(String toEmail,String fullName,String companyName,String jobTitle,
            String locale,String managementUrl,String kind,Instant scheduledStartAt,String timezone){
        boolean vi=locale!=null&&locale.startsWith("vi");
        String subject=switch(kind){
            case "INVITATION" -> vi?"Mời đặt lịch phỏng vấn":"Schedule your interview";
            case "CONFIRMATION" -> vi?"Đã xác nhận lịch phỏng vấn":"Interview confirmed";
            case "RESCHEDULE_CONFIRMATION" -> vi?"Đã đổi lịch phỏng vấn":"Interview rescheduled";
            default -> vi?"Nhắc lịch phỏng vấn":"Interview reminder";
        };
        String schedule=scheduledStartAt==null?"":(vi?"<p>Lịch đã chọn: %s (%s).</p>":"<p>Scheduled time: %s (%s).</p>")
                .formatted(escapeHtml(scheduledStartAt.toString()),escapeHtml(timezone));
        String intro=vi?"Vui lòng quản lý lịch phỏng vấn cho vị trí <strong>%s</strong> tại <strong>%s</strong>."
                .formatted(escapeHtml(jobTitle),escapeHtml(companyName))
                :"Please manage your interview for <strong>%s</strong> at <strong>%s</strong>."
                .formatted(escapeHtml(jobTitle),escapeHtml(companyName));
        String action=vi?"Mở lịch phỏng vấn":"Manage interview";
        EmailMessage message=new EmailMessage(toEmail,fullName,subject,"""
                <!doctype html><html><body style="font-family:Arial,sans-serif;background:#f8fafc;padding:32px">
                <div style="max-width:600px;margin:auto;background:white;padding:36px;border-radius:10px">
                <h1 style="color:#0f172a;font-size:22px">%s</h1><p style="color:#475569;line-height:1.6">%s</p>%s
                <a href="%s" style="display:inline-block;background:#4f46e5;color:white;padding:12px 22px;border-radius:6px;text-decoration:none">%s</a>
                </div></body></html>
                """.formatted(escapeHtml(fullName),intro,schedule,escapeHtml(managementUrl),action));
        sendWithFallback(message);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String headerText(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').trim();
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

    private String buildLogin2FACodeEmailHtml(String fullName, String confirmationCode) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><style>
                body { font-family: Arial, sans-serif; background:#f9f9f9; margin:0; padding:0; }
                .container { max-width:600px; margin:40px auto; background:#fff; border-radius:8px;
                  padding:40px; box-shadow:0 2px 8px rgba(0,0,0,.08); }
                .logo { font-size:24px; font-weight:bold; color:#4f46e5; margin-bottom:24px; }
                h1 { font-size:22px; color:#111827; } p { color:#6b7280; line-height:1.6; }
                .code { display:inline-block; padding:16px 24px; margin:20px 0; border-radius:8px;
                  background:#eef2ff; color:#312e81; font-size:32px; font-weight:bold;
                  letter-spacing:8px; }
                .footer { margin-top:32px; font-size:12px; color:#9ca3af; }
                </style></head><body><div class="container">
                <div class="logo">CacaNode</div>
                <h1>Hello, %s!</h1>
                <p>Enter this confirmation code in the CacaNode mobile app to complete sign-in:</p>
                <div class="code">%s</div>
                <p>If you did not attempt to sign in, you can safely ignore this email.</p>
                <div class="footer">This code expires in 10 minutes and can be used once.</div>
                </div></body></html>
                """.formatted(fullName, confirmationCode);
    }
}
