package com.cacanode.api.notification.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cacanode.api.notification.enums.NotificationStatus;
import com.cacanode.api.notification.enums.NotificationType;
import com.cacanode.api.notification.model.Notification;
import com.cacanode.api.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "NOTIFICATION-SERVICE")
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final EmailService emailService;

  public void sendAndRecordWelcomeEmail(
            UUID tenantId,
            UUID userId,
            String email,
            String fullName,
            String companyName
  ) {
    Notification notification = new Notification();
    notification.setTenantId(tenantId);
    notification.setUserId(userId);
    notification.setType(NotificationType.WELCOME_EMAIL);
    notification.setTitle("Welcome to CacaNode");
    notification.setMessage("Welcome email sent to " + email);
    notification.setStatus(NotificationStatus.PENDING);
    notificationRepository.save(notification);

    try {
        emailService.sendWelcomeEmail(email, fullName, companyName);
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
    } catch (Exception e) {
        notification.setStatus(NotificationStatus.FAILED);
        log.error("Email sending failed: {}", e.getMessage());
    } finally {
        notificationRepository.save(notification);
    }
  }

}
