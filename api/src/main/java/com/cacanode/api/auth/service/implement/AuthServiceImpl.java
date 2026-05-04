package com.cacanode.api.auth.service.implement;

import com.cacanode.api.auth.dto.request.RegisterRequest;
import com.cacanode.api.auth.dto.response.AuthResponse;
import com.cacanode.api.auth.model.RefreshToken;
import com.cacanode.api.auth.repository.RefreshTokenRepository;
import com.cacanode.api.auth.service.AuthService;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.auth.service.JwtService;
import com.cacanode.api.tenant.api.TenantModuleApi;
import com.cacanode.api.tenant.dto.RegisterTenantCommand;
import com.cacanode.api.tenant.dto.TenantUserResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    public AuthResponse register(RegisterRequest req) {

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
        refreshTokenRepository.save(refreshToken);

        // 6. Return response
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .user(AuthResponse.UserInfo.builder()
                        .userId(result.getUserId().toString())
                        .tenantId(result.getTenantId().toString())
                        .email(result.getEmail())
                        .role(result.getRole())
                        .plan(result.getPlan())
                        .build()
                )
                .build();
    }

    @Override
    public boolean isEmailExist(String email) {
        return tenantModuleApi.existsByEmail(email);
    }

}
