package com.cacanode.api.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MobileLogoutRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
