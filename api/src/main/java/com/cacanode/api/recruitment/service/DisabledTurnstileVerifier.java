package com.cacanode.api.recruitment.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false} and not ${app.recruitment.public.turnstile-enabled:false}")
public class DisabledTurnstileVerifier implements TurnstileVerifier {
    @Override public boolean verify(String token, String remoteIp) { return true; }
}
