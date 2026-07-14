package com.cacanode.api.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MobileAuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private AuthResponse.UserInfo user;
}
