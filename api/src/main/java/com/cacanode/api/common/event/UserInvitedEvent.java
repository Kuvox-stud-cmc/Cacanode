package com.cacanode.api.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class UserInvitedEvent extends ApplicationEvent {
    private final UUID tenantId;
    private final UUID invitedByUserId;
    private final String email;
    private final String tenantName;
    private final String role;
    private final String token;
    private final LocalDateTime expiresAt;

    public UserInvitedEvent(Object source, UUID tenantId, UUID invitedByUserId, String email,
                            String tenantName, String role, String token, LocalDateTime expiresAt) {
        super(source);
        this.tenantId = tenantId;
        this.invitedByUserId = invitedByUserId;
        this.email = email;
        this.tenantName = tenantName;
        this.role = role;
        this.token = token;
        this.expiresAt = expiresAt;
    }
}
