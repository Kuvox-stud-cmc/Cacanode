package com.cacanode.api.auth.service;

import com.cacanode.api.auth.dto.request.LoginRequest;
import com.cacanode.api.auth.dto.request.MobileLoginRequest;
import com.cacanode.api.auth.dto.request.RegisterRequest;
import com.cacanode.api.auth.dto.response.AuthResponse;
import com.cacanode.api.auth.dto.response.MobileAuthResponse;
import com.cacanode.api.auth.dto.response.RegisterResponse;
import com.cacanode.api.auth.dto.response.ResendVerificationResponse;
import com.cacanode.api.tenant.dto.UserAuthDto;

import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    boolean isEmailExist(String email);

    void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, boolean persistent);

    void clearRefreshTokenCookie(HttpServletResponse response);

    RegisterResponse register(RegisterRequest req);

    AuthResponse verifyEmail(String token, HttpServletResponse res);

    Object login(LoginRequest req, HttpServletResponse res);

    Object mobileLogin(MobileLoginRequest req);

    AuthResponse verifyLogin2FA(String token, HttpServletResponse res);

    MobileAuthResponse mobileVerifyLogin2FA(String email, String code);

    ResendVerificationResponse resendLogin2FA(String email);

    void logout(String refreshToken);

    AuthResponse refreshToken(String refreshToken, HttpServletResponse res);

    MobileAuthResponse mobileRefreshToken(String refreshToken);

    ResendVerificationResponse resendVerificationEmail(String email);

    AuthResponse issueAuthTokens(UserAuthDto user, HttpServletResponse response, boolean persistent);
}
