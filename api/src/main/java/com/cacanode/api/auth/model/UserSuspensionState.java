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
        name = "user_suspension_state",
        indexes = {
                @Index(name = "idx_suspension_user_id", columnList = "user_id", unique = true)
        }
)
public class UserSuspensionState extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "reason", nullable = false, length = 100)
    private String reason;

    @Column(name = "suspended_at", nullable = false)
    private LocalDateTime suspendedAt;

    @PrePersist
    protected void onCreate() {
        if (suspendedAt == null) {
            suspendedAt = LocalDateTime.now();
        }
    }
}
