package com.cacanode.api.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class UserRegisteredEvent extends ApplicationEvent {

    private UUID userId;
    private UUID tenantId;
    private String email;

    public UserRegisteredEvent(Object source, UUID userId, UUID tenantId, String email) {
        super(source);
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
    }

}
