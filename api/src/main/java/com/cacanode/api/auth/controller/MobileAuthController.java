package com.cacanode.api.auth.controller;

import com.cacanode.api.auth.dto.request.MobileLoginRequest;
import com.cacanode.api.auth.dto.request.MobileLogoutRequest;
import com.cacanode.api.auth.dto.request.MobileRefreshRequest;
import com.cacanode.api.auth.dto.request.MobileVerifyLogin2FARequest;
import com.cacanode.api.auth.dto.response.MobileAuthResponse;
import com.cacanode.api.auth.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mobile Authentication", description = "Native client authentication endpoints")
@RestController
@RequestMapping("/api/v1/auth/mobile")
@RequiredArgsConstructor
public class MobileAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody MobileLoginRequest request) {
        return ResponseEntity.ok(authService.mobileLogin(request));
    }

    @PostMapping("/verify-login-2fa")
    public ResponseEntity<MobileAuthResponse> verifyLogin2FA(
            @Valid @RequestBody MobileVerifyLogin2FARequest request) {
        return ResponseEntity.ok(authService.mobileVerifyLogin2FA(request.getEmail(), request.getCode()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<MobileAuthResponse> refresh(
            @Valid @RequestBody MobileRefreshRequest request) {
        return ResponseEntity.ok(authService.mobileRefreshToken(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody MobileLogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
