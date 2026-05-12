package com.cacanode.api.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

import com.cacanode.api.common.model.BaseEntity;

@Getter
@Setter
@Entity
@Table(
        name = "verification_resend_state",
        indexes = {
                @Index(name = "idx_verification_resend_user_id", columnList = "user_id", unique = true)
        }
)
public class VerificationResendState extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @PrePersist
    protected void onCreate() {
        if (attemptCount == null) {
            attemptCount = 0;
        }
    }
}
