package com.cacanode.api.common.event;

import com.cacanode.api.auth.enums.Login2FAChallengeType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class Login2FARequestedEvent extends ApplicationEvent {

    private UUID userId;
    private UUID tenantId;
    private String email;
    private String fullName;
    private String verificationSecret;
    private Login2FAChallengeType challengeType;

    public Login2FARequestedEvent(
            Object source,
            UUID userId,
            UUID tenantId,
            String email,
            String fullName,
            String verificationSecret,
            Login2FAChallengeType challengeType) {
        super(source);
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.fullName = fullName;
        this.verificationSecret = verificationSecret;
        this.challengeType = challengeType;
    }

}
