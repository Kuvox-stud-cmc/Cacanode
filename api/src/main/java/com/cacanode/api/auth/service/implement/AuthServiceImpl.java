package com.cacanode.api.auth.service.implement;

import com.cacanode.api.auth.dto.request.LoginRequest;
import com.cacanode.api.auth.dto.request.RegisterRequest;
import com.cacanode.api.auth.dto.response.AuthResponse;
import com.cacanode.api.auth.model.RefreshToken;
import com.cacanode.api.auth.repository.RefreshTokenRepository;
import com.cacanode.api.auth.service.AuthService;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.auth.service.JwtService;
import com.cacanode.api.tenant.api.RegisterTenantCommand;
import com.cacanode.api.tenant.api.TenantModuleApi;
import com.cacanode.api.tenant.api.TenantUserResult;
import com.cacanode.api.tenant.dto.UserAuthDto;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j(topic = "AUTH-SERVICE")
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    @Value("${jwt.expiry-days}")
    private Long refreshTokenExpiryTime;

    private final TenantModuleApi tenantModuleApi;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest req, HttpServletResponse res) {

        // 1. Check email uniqueness
        if(isEmailExist(req.getEmail())) {
            throw new ConflictException("Email already exists: " + req.getEmail());
        }

        // 2. Hash password - auth module owns this concern
        String passwordHash = passwordEncoder.encode(req.getPassword());

        // 3. Call tenant module via interface
        //    Tenant module creates both Tenant and User records
        TenantUserResult result = tenantModuleApi.registerTenantWithAdmin(
                RegisterTenantCommand.builder()
                        .companyName(req.getCompanyName())
                        .fullName(req.getFullName())
                        .email(req.getEmail())
                        .passwordHash(passwordHash)
                        .build()
        );

        log.info("Tenant registered: tenantId={}, userId={}", result.getTenantId(), result.getUserId());

        // 4. Generate tokens
        String accessToken = jwtService.generateAccessToken(
                result.getUserId(),
                result.getTenantId(),
                result.getEmail(),
                result.getRole()
        );
        String refreshTokenValue = jwtService.generateRefreshToken();

        // 5. Persist refresh token - auth module owns refresh_tokens table
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(result.getUserId());
        refreshToken.setTenantId(result.getTenantId());
        refreshToken.setTokenHash(jwtService.hashToken(refreshTokenValue));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryTime));
        refreshToken.setRevoked(false);
        refreshToken.setPersistent(true);
        refreshTokenRepository.save(refreshToken);

        // Set refresh token in HttpOnly cookie (persistent after registration)
        setRefreshTokenCookie(res, refreshTokenValue, true);

        // 6. Return response
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .user(AuthResponse.UserInfo.builder()
                        .userId(result.getUserId().toString())
                        .tenantId(result.getTenantId().toString())
                        .email(result.getEmail())
                        .fullName(result.getFullName())
                        .role(result.getRole())
                        .plan(result.getPlan())
                        .build()
                )
                .build();
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest req, HttpServletResponse res) {

        // 1. Call tenant module to authenticate user
        TenantUserResult result = tenantModuleApi.authenticateUser(req.getEmail(), req.getPassword());

        // 2. If authentication fails, tenant module returns null - we throw exception here since it's auth related
        if(result == null) {
            throw new ConflictException("Invalid email or password");
        }

        // 3. Delete existing refresh tokens for the user
        deleteRefreshTokensByUserId(result.getUserId());

        // 4. Generate tokens
        String accessToken = jwtService.generateAccessToken(
                result.getUserId(),
                result.getTenantId(),
                result.getEmail(),
                result.getRole()
        );
        String refreshTokenValue = jwtService.generateRefreshToken();

        boolean persistent = req.isRememberMe();

        // 5. Persist refresh token - auth module owns refresh_tokens table
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(result.getUserId());
        refreshToken.setTenantId(result.getTenantId());
        refreshToken.setTokenHash(jwtService.hashToken(refreshTokenValue));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryTime));
        refreshToken.setRevoked(false);
        refreshToken.setPersistent(persistent);
        refreshTokenRepository.save(refreshToken);

        // 6. Set refresh token in HttpOnly cookie and return response
        setRefreshTokenCookie(res, refreshTokenValue, persistent);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .user(AuthResponse.UserInfo.builder()
                        .userId(result.getUserId().toString())
                        .tenantId(result.getTenantId().toString())
                        .email(result.getEmail())
                        .fullName(result.getFullName())
                        .role(result.getRole())
                        .plan(result.getPlan())
                        .build()
                )
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = jwtService.hashToken(refreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshToken, HttpServletResponse res) {
        String tokenHash = jwtService.hashToken(refreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
    
        if (stored.isRevoked()) {
            throw new UnauthorizedException("Refresh token revoked");
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token expired");
        }

        boolean persistent = stored.isPersistent();

        // Rotate — delete old, issue new
        refreshTokenRepository.delete(stored);

        String newRefreshToken = jwtService.generateRefreshToken();
        RefreshToken newStored = new RefreshToken();
        newStored.setUserId(stored.getUserId());
        newStored.setTenantId(stored.getTenantId());
        newStored.setTokenHash(jwtService.hashToken(newRefreshToken));
        newStored.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryTime));
        newStored.setRevoked(false);
        newStored.setPersistent(persistent);
        refreshTokenRepository.save(newStored);

        // Set new cookie (same persistence as previous refresh token)
        setRefreshTokenCookie(res, newRefreshToken, persistent);

        UserAuthDto user = tenantModuleApi.findUserById(stored.getUserId());
        if (user == null) {
            throw new UnauthorizedException("User not found");
        }

        String newAccessToken = jwtService.generateAccessToken(
            stored.getUserId(),
            stored.getTenantId(),
            user.getEmail(),
            user.getRole()
        );

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .user(AuthResponse.UserInfo.builder()
                        .userId(stored.getUserId().toString())
                        .tenantId(stored.getTenantId().toString())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole())
                        .plan(user.getPlan())
                        .build())
                .build();
    }

    @Override
    public boolean isEmailExist(String email) {
        return tenantModuleApi.existsByEmail(email);
    }

    @Override
    public void deleteRefreshTokensByUserId(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Override
    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, boolean persistent) {
        Duration maxAge = persistent
                ? Duration.ofDays(refreshTokenExpiryTime)
                : Duration.ofSeconds(-1);
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/api/auth")
                .maxAge(maxAge)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)              // ← 0 deletes the cookie
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
