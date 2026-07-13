package com.cacanode.api.auth.service.implement;

import com.cacanode.api.auth.dto.request.LoginRequest;
import com.cacanode.api.auth.dto.request.RegisterRequest;
import com.cacanode.api.auth.dto.response.AuthResponse;
import com.cacanode.api.auth.dto.response.LoginStep1Response;
import com.cacanode.api.auth.dto.response.RegisterResponse;
import com.cacanode.api.auth.dto.response.ResendVerificationResponse;
import com.cacanode.api.auth.model.Login2FAState;
import com.cacanode.api.auth.model.RefreshToken;
import com.cacanode.api.auth.model.UserSuspensionState;
import com.cacanode.api.auth.model.VerificationResendState;
import com.cacanode.api.auth.repository.Login2FAStateRepository;
import com.cacanode.api.auth.repository.UserSuspensionStateRepository;
import com.cacanode.api.auth.repository.VerificationResendStateRepository;
import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.event.AuditLogEvent;
import com.cacanode.api.common.event.Login2FARequestedEvent;
import com.cacanode.api.common.event.UserRegisteredEvent;
import io.jsonwebtoken.Claims;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j(topic = "AUTH-SERVICE")
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    @Value("${jwt.expiry-days}")
    private Long refreshTokenExpiryTime;

    @Value("${spring.sendgrid.support-email:support@cacanode.com}")
    private String supportEmail;

    @Value("${spring.sendgrid.verification-resend-cooldown-seconds:60}")
    private Integer verificationResendCooldownSeconds;

    @Value("${spring.sendgrid.max-verification-resend-attempts:5}")
    private Integer maxVerificationResendAttempts;

    @Value("${spring.sendgrid.login-2fa-expiry-minutes:15}")
    private Integer login2FAExpiryMinutes;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.security.login-2fa-bypass-emails:}")
    private String login2FABypassEmails;

    private final TenantModuleApi tenantModuleApi;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSuspensionStateRepository userSuspensionStateRepository;
    private final Login2FAStateRepository login2FAStateRepository;
    private final VerificationResendStateRepository verificationResendStateRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest req) {

        // 1. Check email uniqueness
        if (isEmailExist(req.getEmail())) {
            throw new ConflictException("Email already exists: " + req.getEmail());
        }

        // 2. Hash password - auth module owns this concern
        String passwordHash = passwordEncoder.encode(req.getPassword());

        // 3. Call tenant module via interface
        // Tenant module creates both Tenant and User records
        TenantUserResult result = tenantModuleApi.registerTenantWithAdmin(
                RegisterTenantCommand.builder()
                        .companyName(req.getCompanyName())
                        .fullName(req.getFullName())
                        .email(req.getEmail())
                        .passwordHash(passwordHash)
                        .build());

        log.info("Tenant registered: tenantId={}, userId={}", result.getTenantId(), result.getUserId());

        // 4. Generate verification token with the ACTUAL userId from tenant module
        String verificationToken = jwtService.generateVerificationToken(result.getUserId(), result.getEmail());

        // 5. Publish event to trigger verification email
        // Notification module will listen and send the email
        eventPublisher.publishEvent(
                new UserRegisteredEvent(this, result.getUserId(), result.getTenantId(), result.getEmail(),
                        req.getFullName(), req.getCompanyName(), verificationToken));

        // 6. Return response (no tokens - user must verify email first)
        return RegisterResponse.builder()
                .message("Registration successful. Please check your email to verify your account.")
                .email(result.getEmail())
                .tenantId(result.getTenantId().toString())
                .userId(result.getUserId().toString())
                .build();
    }

    @Override
    @Transactional
    public Object login(LoginRequest req, HttpServletResponse res) {

        // 1. Call tenant module to authenticate user
        TenantUserResult result = tenantModuleApi.authenticateUser(req.getEmail(), req.getPassword());

        // 2. If authentication fails, tenant module returns null - we throw exception
        if (result == null) {
            throw new ConflictException("Invalid email or password");
        }

        // 3. Check if user is suspended
        if ("SUSPENDED".equals(result.getStatus())) {
            throw new UnauthorizedException(
                    "Account suspended. Please contact " + supportEmail + " for assistance.");
        }

        // 4. Check for existing 2FA attempt and suspension state
        Login2FAState existingState = login2FAStateRepository.findByEmail(req.getEmail())
                .orElse(null);

        UserSuspensionState suspensionState = userSuspensionStateRepository.findByUserId(result.getUserId())
                .orElse(null);

        if (suspensionState != null) {
            throw new UnauthorizedException(
                    "Account suspended due to verification abuse. Please contact " + supportEmail + " for assistance.");
        }

        if (isLogin2FABypassed(result.getEmail())) {
            UserAuthDto user = tenantModuleApi.findUserById(result.getUserId());
            if (user == null) {
                throw new UnauthorizedException("User not found");
            }

            log.info("Login 2FA bypassed for configured dev account: userId={}, email={}",
                    result.getUserId(), result.getEmail());

            return issueAuthTokens(user, res, req.isRememberMe());
        }

        // 5. Generate 2FA token
        String verificationToken = jwtService.generateVerificationToken(result.getUserId(), result.getEmail());

        // 6. Create or update login 2FA state
        if (existingState == null) {
            existingState = new Login2FAState();
            existingState.setUserId(result.getUserId());
            existingState.setEmail(result.getEmail());
            existingState.setAttemptCount(1);
        } else {
            existingState.setAttemptCount(existingState.getAttemptCount() + 1);
        }
        existingState.setTokenHash(jwtService.hashToken(verificationToken));
        existingState.setExpiresAt(LocalDateTime.now().plusMinutes(login2FAExpiryMinutes));
        existingState.setUsed(false);
        login2FAStateRepository.save(existingState);

        // 7. Publish event to send 2FA email
        eventPublisher.publishEvent(
                new Login2FARequestedEvent(this, result.getUserId(), result.getTenantId(),
                        result.getEmail(), result.getFullName(), verificationToken));

        // 8. Publish audit log event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("attemptCount", existingState.getAttemptCount());
        metadata.put("email", result.getEmail());
        eventPublisher.publishEvent(
                AuditLogEvent.builder(this)
                        .tenantId(result.getTenantId())
                        .userId(result.getUserId())
                        .action(LogAction.LOGIN_2FA_REQUESTED)
                        .resourceType("login_2fa")
                        .resourceId(existingState.getId())
                        .metadata(metadata)
                        .build());

        log.info("Login 2FA requested: userId={}, email={}", result.getUserId(), result.getEmail());

        // 9. Return step 1 response
        return LoginStep1Response.builder()
                .message("Please check your email for a verification link to complete login.")
                .email(result.getEmail())
                .requires2FA(true)
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

        UserAuthDto user = tenantModuleApi.findUserById(stored.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("User account is disabled");
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

        String newAccessToken = jwtService.generateAccessToken(
                stored.getUserId(),
                stored.getTenantId(),
                user.getEmail(),
                user.getRole());

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
                .secure(cookieSecure)
                .path("/api")
                .maxAge(maxAge)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api")
                .maxAge(0) // ← 0 deletes the cookie
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Override
    @Transactional
    public AuthResponse verifyEmail(String token, HttpServletResponse res) {
        // 1. Validate the verification token
        Claims claims = jwtService.validateVerificationToken(token);
        UUID userId = UUID.fromString(claims.get("userId", String.class));

        // 2. Activate the user (tenant module owns users table)
        UserAuthDto user = tenantModuleApi.activateUser(userId);
        if (user == null) {
            throw new UnauthorizedException("User not found");
        }

        log.info("User email verified and activated: userId={}, email={}", userId, user.getEmail());

        return issueAuthTokens(user, res, true);
    }

    @Override
    @Transactional
    public ResendVerificationResponse resendVerificationEmail(String email) {
        // 1. Find user by email
        UserAuthDto user = tenantModuleApi.findUserByEmail(email);
        if (user == null || !"PENDING".equals(user.getStatus())) {
            throw new UnauthorizedException("No pending verification found for this email");
        }

        UUID userId = user.getUserId();
        UUID tenantId = user.getTenantId();

        // 2. Check for existing resend state
        VerificationResendState resendState = verificationResendStateRepository.findByUserId(userId)
                .orElse(null);

        // 3. Check if user is suspended
        UserSuspensionState suspensionState = userSuspensionStateRepository.findByUserId(userId)
                .orElse(null);
        if (suspensionState != null) {
            throw new UnauthorizedException(
                    "Account suspended due to verification abuse. Please contact " + supportEmail + " for assistance.");
        }

        // 4. Check attempt limits
        if (resendState != null && resendState.getAttemptCount() >= maxVerificationResendAttempts) {
            // Suspend the user
            tenantModuleApi.suspendUser(userId);

            // Create suspension record
            UserSuspensionState newSuspension = new UserSuspensionState();
            newSuspension.setUserId(userId);
            newSuspension.setReason("VERIFICATION_RESEND_ABUSE");
            userSuspensionStateRepository.save(newSuspension);

            // Update resend state
            resendState.setSuspendedAt(LocalDateTime.now());
            verificationResendStateRepository.save(resendState);

            // Publish audit log event
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("attemptCount", resendState.getAttemptCount());
            metadata.put("email", email);
            metadata.put("reason", "VERIFICATION_RESEND_ABUSE");
            eventPublisher.publishEvent(
                    AuditLogEvent.builder(this)
                            .tenantId(tenantId)
                            .userId(userId)
                            .action(LogAction.USER_SUSPENDED_VERIFICATION_ABUSE)
                            .resourceType("user_suspension")
                            .metadata(metadata)
                            .build());

            log.warn("User suspended due to verification resend abuse: userId={}, email={}", userId, email);
            throw new UnauthorizedException(
                    "Account suspended due to verification abuse. Please contact " + supportEmail + " for assistance.");
        }

        // 5. Check cooldown
        if (resendState != null && resendState.getLastAttemptAt() != null) {
            java.time.Duration sinceLastAttempt = java.time.Duration.between(resendState.getLastAttemptAt(),
                    LocalDateTime.now());
            if (sinceLastAttempt.getSeconds() < verificationResendCooldownSeconds) {
                int remainingSeconds = verificationResendCooldownSeconds - (int) sinceLastAttempt.getSeconds();

                // Publish audit log for rate limit
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("attemptCount", resendState.getAttemptCount());
                metadata.put("email", email);
                metadata.put("remainingSeconds", remainingSeconds);
                eventPublisher.publishEvent(
                        AuditLogEvent.builder(this)
                                .tenantId(tenantId)
                                .userId(userId)
                                .action(LogAction.VERIFICATION_EMAIL_RESEND_LIMIT_EXCEEDED)
                                .resourceType("verification_resend")
                                .metadata(metadata)
                                .build());

                return ResendVerificationResponse.builder()
                        .message("Please wait before requesting another verification email")
                        .canRetryAfterSeconds(remainingSeconds)
                        .build();
            }
        }

        // 6. Generate new verification token
        String verificationToken = jwtService.generateVerificationToken(userId, email);

        // 7. Publish event to send email (reuse UserRegisteredEvent with new token)
        eventPublisher.publishEvent(
                new UserRegisteredEvent(this, userId, tenantId, email,
                        user.getFullName(), "Company", verificationToken));

        // 8. Update or create resend state
        if (resendState == null) {
            resendState = new VerificationResendState();
            resendState.setUserId(userId);
            resendState.setAttemptCount(1);
        } else {
            resendState.setAttemptCount(resendState.getAttemptCount() + 1);
        }
        resendState.setLastAttemptAt(LocalDateTime.now());
        verificationResendStateRepository.save(resendState);

        // 9. Publish audit log event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("attemptCount", resendState.getAttemptCount());
        metadata.put("email", email);
        eventPublisher.publishEvent(
                AuditLogEvent.builder(this)
                        .tenantId(tenantId)
                        .userId(userId)
                        .action(LogAction.VERIFICATION_EMAIL_RESEND_REQUESTED)
                        .resourceType("verification_resend")
                        .resourceId(resendState.getId())
                        .metadata(metadata)
                        .build());

        log.info("Verification email resent: userId={}, email={}, attempt={}", userId, email,
                resendState.getAttemptCount());

        return ResendVerificationResponse.builder()
                .message("Verification email sent. Please check your inbox.")
                .build();
    }

    @Override
    @Transactional
    public AuthResponse verifyLogin2FA(String token, HttpServletResponse res) {
        // 1. Validate the 2FA token
        Claims claims = jwtService.validateVerificationToken(token);
        UUID userId = UUID.fromString(claims.get("userId", String.class));
        String email = claims.getSubject();

        // 2. Find the login 2FA state
        Login2FAState state = login2FAStateRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired login verification"));

        // 3. Check if already used
        if (state.isUsed()) {
            throw new UnauthorizedException("This verification link has already been used");
        }

        // 4. Check if user is suspended
        UserSuspensionState suspensionState = userSuspensionStateRepository.findByUserId(userId)
                .orElse(null);
        if (suspensionState != null) {
            throw new UnauthorizedException(
                    "Account suspended due to verification abuse. Please contact " + supportEmail + " for assistance.");
        }

        // 5. Check if expired
        if (state.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Login verification link has expired");
        }

        // 6. Verify token hash matches
        String tokenHash = jwtService.hashToken(token);
        if (!tokenHash.equals(state.getTokenHash())) {
            throw new UnauthorizedException("Invalid verification token");
        }

        // 7. Get user info
        UserAuthDto user = tenantModuleApi.findUserById(userId);
        if (user == null) {
            throw new UnauthorizedException("User not found");
        }

        // 8. Mark state as used
        state.setUsed(true);
        login2FAStateRepository.save(state);

        // 9. Publish audit log event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("attemptCount", state.getAttemptCount());
        metadata.put("email", email);
        eventPublisher.publishEvent(
                AuditLogEvent.builder(this)
                        .tenantId(user.getTenantId())
                        .userId(userId)
                        .action(LogAction.LOGIN_2FA_VERIFIED)
                        .resourceType("login_2fa")
                        .resourceId(state.getId())
                        .metadata(metadata)
                        .build());

        log.info("Login 2FA verified: userId={}, email={}", userId, email);

        return issueAuthTokens(user, res, true);
    }

    private boolean isLogin2FABypassed(String email) {
        if (login2FABypassEmails == null || login2FABypassEmails.isBlank()) {
            return false;
        }

        for (String allowedEmail : login2FABypassEmails.split(",")) {
            if (allowedEmail.trim().equalsIgnoreCase(email)) {
                return true;
            }
        }

        return false;
    }

    @Override
    @Transactional
    public AuthResponse issueAuthTokens(UserAuthDto user, HttpServletResponse res, boolean persistent) {
        deleteRefreshTokensByUserId(user.getUserId());

        String accessToken = jwtService.generateAccessToken(
                user.getUserId(),
                user.getTenantId(),
                user.getEmail(),
                user.getRole());
        String refreshTokenValue = jwtService.generateRefreshToken();

        // 12. Persist refresh token - auth module owns refresh_tokens table
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getUserId());
        refreshToken.setTenantId(user.getTenantId());
        refreshToken.setTokenHash(jwtService.hashToken(refreshTokenValue));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryTime));
        refreshToken.setRevoked(false);
        refreshToken.setPersistent(persistent);
        refreshTokenRepository.save(refreshToken);

        setRefreshTokenCookie(res, refreshTokenValue, persistent);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirySeconds())
                .user(AuthResponse.UserInfo.builder()
                        .userId(user.getUserId().toString())
                        .tenantId(user.getTenantId().toString())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole())
                        .plan(user.getPlan())
                        .build())
                .build();
    }

    @Override
    @Transactional
    public ResendVerificationResponse resendLogin2FA(String email) {
        // 1. Find existing 2FA state
        Login2FAState state = login2FAStateRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("No pending login verification found"));

        // 2. Check if user is suspended
        UserSuspensionState suspensionState = userSuspensionStateRepository.findByUserId(state.getUserId())
                .orElse(null);
        if (suspensionState != null) {
            throw new UnauthorizedException(
                    "Account suspended due to verification abuse. Please contact " + supportEmail + " for assistance.");
        }

        // 3. Check if already used
        if (state.isUsed()) {
            throw new UnauthorizedException("Login verification already completed");
        }

        // 4. Get user info
        UserAuthDto user = tenantModuleApi.findUserById(state.getUserId());
        if (user == null) {
            throw new UnauthorizedException("User not found");
        }
        UUID tenantId = user.getTenantId();
        UUID userId = state.getUserId();

        // 5. Check attempt limits
        if (state.getAttemptCount() >= maxVerificationResendAttempts) {
            // Suspend the user account
            tenantModuleApi.suspendUser(userId);

            // Create suspension record
            UserSuspensionState newSuspension = new UserSuspensionState();
            newSuspension.setUserId(userId);
            newSuspension.setReason("LOGIN_2FA_RESEND_ABUSE");
            userSuspensionStateRepository.save(newSuspension);

            // Publish audit log event
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("attemptCount", state.getAttemptCount());
            metadata.put("email", email);
            metadata.put("reason", "LOGIN_2FA_RESEND_ABUSE");
            eventPublisher.publishEvent(
                    AuditLogEvent.builder(this)
                            .tenantId(tenantId)
                            .userId(userId)
                            .action(LogAction.USER_SUSPENDED_VERIFICATION_ABUSE)
                            .resourceType("user_suspension")
                            .metadata(metadata)
                            .build());

            log.warn("User suspended due to login 2FA abuse: userId={}, email={}", userId, email);
            throw new UnauthorizedException(
                    "Account suspended due to verification abuse. Please contact " + supportEmail + " for assistance.");
        }

        // 6. Check cooldown (use createdAt for cooldown calculation)
        java.time.Duration sinceLastAttempt = java.time.Duration.between(
                state.getUpdatedAt(), LocalDateTime.now());
        if (sinceLastAttempt.getSeconds() < verificationResendCooldownSeconds) {
            int remainingSeconds = verificationResendCooldownSeconds - (int) sinceLastAttempt.getSeconds();

            // Publish audit log for rate limit
            Map<String, Object> rateLimitMetadata = new HashMap<>();
            rateLimitMetadata.put("attemptCount", state.getAttemptCount());
            rateLimitMetadata.put("email", email);
            rateLimitMetadata.put("remainingSeconds", remainingSeconds);
            eventPublisher.publishEvent(
                    AuditLogEvent.builder(this)
                            .tenantId(tenantId)
                            .userId(userId)
                            .action(LogAction.LOGIN_2FA_RESEND_LIMIT_EXCEEDED)
                            .resourceType("login_2fa")
                            .metadata(rateLimitMetadata)
                            .build());

            return ResendVerificationResponse.builder()
                    .message("Please wait before requesting another verification email")
                    .canRetryAfterSeconds(remainingSeconds)
                    .build();
        }

        // 7. Generate new 2FA token
        String verificationToken = jwtService.generateVerificationToken(userId, email);

        // 8. Update state
        state.setAttemptCount(state.getAttemptCount() + 1);
        state.setTokenHash(jwtService.hashToken(verificationToken));
        state.setExpiresAt(LocalDateTime.now().plusMinutes(login2FAExpiryMinutes));
        login2FAStateRepository.save(state);

        // 9. Publish event to send 2FA email
        eventPublisher.publishEvent(
                new Login2FARequestedEvent(this, userId, tenantId,
                        email, user.getFullName(), verificationToken));

        // 10. Publish audit log event
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("attemptCount", state.getAttemptCount());
        metadata.put("email", email);
        eventPublisher.publishEvent(
                AuditLogEvent.builder(this)
                        .tenantId(tenantId)
                        .userId(userId)
                        .action(LogAction.LOGIN_2FA_RESEND_REQUESTED)
                        .resourceType("login_2fa")
                        .resourceId(state.getId())
                        .metadata(metadata)
                        .build());

        log.info("Login 2FA resent: userId={}, email={}, attempt={}", userId, email,
                state.getAttemptCount());

        return ResendVerificationResponse.builder()
                .message("Verification email sent. Please check your inbox.")
                .build();
    }
}
