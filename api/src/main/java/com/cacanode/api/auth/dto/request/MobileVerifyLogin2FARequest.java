package com.cacanode.api.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MobileVerifyLogin2FARequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Confirmation code is required")
    @Pattern(regexp = "\\d{6}", message = "Confirmation code must contain exactly 6 digits")
    private String code;
}
