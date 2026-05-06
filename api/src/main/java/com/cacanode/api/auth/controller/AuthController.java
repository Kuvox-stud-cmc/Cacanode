package com.cacanode.api.auth.controller;

import com.cacanode.api.auth.dto.request.LoginRequest;
import com.cacanode.api.auth.dto.request.RegisterRequest;
import com.cacanode.api.auth.dto.response.AuthResponse;
import com.cacanode.api.auth.service.AuthService;
import com.cacanode.api.common.exception.custom.UnauthorizedException;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

        private final AuthService authService;

        @PostMapping("/register")
        public ResponseEntity<AuthResponse> register(
                @Valid @RequestBody RegisterRequest request,
                HttpServletResponse response
        ) {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(authService.register(request, response));
        }

        @PostMapping("/login")
        public ResponseEntity<AuthResponse> login(
                @Valid @RequestBody LoginRequest request,
                HttpServletResponse response
        ) { 
            return ResponseEntity.ok(authService.login(request, response));
        }
        
        @PostMapping("/refresh")
        public ResponseEntity<AuthResponse> refresh(
                @CookieValue(name = "refresh_token", required = false) String refreshToken,
                HttpServletResponse response
        ) {
                if (refreshToken == null) {
                        throw new UnauthorizedException("Refresh token missing");
                }
                return ResponseEntity.ok(authService.refreshToken(refreshToken, response));
        }

        @PostMapping("/logout")
        public ResponseEntity<Void> logout(
                @CookieValue(name = "refresh_token", required = false) String refreshToken,
                HttpServletResponse response
        ) {
                if (refreshToken != null) {
                        authService.logout(refreshToken);
                }
                authService.clearRefreshTokenCookie(response);
                return ResponseEntity.noContent().build();
        }
}
