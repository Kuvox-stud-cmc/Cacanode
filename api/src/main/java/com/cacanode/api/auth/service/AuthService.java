package com.cacanode.api.auth.service;

import java.util.UUID;

import com.cacanode.api.auth.dto.request.LoginRequest;
import com.cacanode.api.auth.dto.request.RegisterRequest;
import com.cacanode.api.auth.dto.response.AuthResponse;

import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    boolean isEmailExist(String email);

    void deleteRefreshTokensByUserId(UUID userId);

    void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, boolean persistent);

    void clearRefreshTokenCookie(HttpServletResponse response);

    AuthResponse register(RegisterRequest req, HttpServletResponse res);

    AuthResponse login(LoginRequest req, HttpServletResponse res);
        
    void logout(String refreshToken);
    
    AuthResponse refreshToken(String refreshToken, HttpServletResponse res);
}
