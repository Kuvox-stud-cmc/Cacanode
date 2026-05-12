package com.cacanode.api.auth.service;

import java.util.UUID;

import com.cacanode.api.auth.dto.request.LoginRequest;
import com.cacanode.api.auth.dto.request.RegisterRequest;
import com.cacanode.api.auth.dto.response.AuthResponse;
import com.cacanode.api.auth.dto.response.RegisterResponse;
import com.cacanode.api.auth.dto.response.ResendVerificationResponse;

import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    boolean isEmailExist(String email);

    void deleteRefreshTokensByUserId(UUID userId);

    void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, boolean persistent);

    void clearRefreshTokenCookie(HttpServletResponse response);

    RegisterResponse register(RegisterRequest req);

    AuthResponse verifyEmail(String token, HttpServletResponse res);

    Object login(LoginRequest req, HttpServletResponse res);

    AuthResponse verifyLogin2FA(String token, HttpServletResponse res);

    ResendVerificationResponse resendLogin2FA(String email);

    void logout(String refreshToken);

    AuthResponse refreshToken(String refreshToken, HttpServletResponse res);

    ResendVerificationResponse resendVerificationEmail(String email);
}
