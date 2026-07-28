package com.cacanode.api.recruitment.service;

public interface TurnstileVerifier {
    boolean verify(String token, String remoteIp);
}
