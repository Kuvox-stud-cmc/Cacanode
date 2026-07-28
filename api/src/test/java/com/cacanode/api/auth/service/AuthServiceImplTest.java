package com.cacanode.api.auth.service;

import com.cacanode.api.auth.dto.request.MobileLoginRequest;
import com.cacanode.api.auth.dto.request.LoginRequest;
import com.cacanode.api.auth.dto.response.LoginStep1Response;
import com.cacanode.api.auth.dto.response.MobileAuthResponse;
import com.cacanode.api.auth.api.Login2FAChallengeType;
import com.cacanode.api.auth.model.Login2FAState;
import com.cacanode.api.auth.model.RefreshToken;
import com.cacanode.api.auth.repository.Login2FAStateRepository;
import com.cacanode.api.auth.repository.RefreshTokenRepository;
import com.cacanode.api.auth.repository.UserSuspensionStateRepository;
import com.cacanode.api.auth.repository.VerificationResendStateRepository;
import com.cacanode.api.auth.service.implement.AuthServiceImpl;
import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.auth.api.event.Login2FARequestedEvent;
import com.cacanode.api.tenant.api.TenantIdentityApi;
import com.cacanode.api.tenant.api.TenantUserResult;
import com.cacanode.api.tenant.api.UserAuthDto;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEvent;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private TenantIdentityApi tenants;
    private RefreshTokenRepository refreshTokens;
    private UserSuspensionStateRepository suspensions;
    private Login2FAStateRepository loginStates;
    private VerificationResendStateRepository resendStates;
    private JwtService jwt;
    private Login2FAAttemptService loginAttempts;
    private PasswordEncoder passwordEncoder;
    private ApplicationEventPublisher events;
    private AuthServiceImpl service;

    private UUID userId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantIdentityApi.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        suspensions = mock(UserSuspensionStateRepository.class);
        loginStates = mock(Login2FAStateRepository.class);
        resendStates = mock(VerificationResendStateRepository.class);
        jwt = mock(JwtService.class);
        loginAttempts = mock(Login2FAAttemptService.class);
        passwordEncoder = new BCryptPasswordEncoder();
        events = mock(ApplicationEventPublisher.class);
        service = new AuthServiceImpl(
                tenants,
                refreshTokens,
                suspensions,
                loginStates,
                resendStates,
                jwt,
                loginAttempts,
                passwordEncoder,
                events);

        ReflectionTestUtils.setField(service, "refreshTokenExpiryTime", 30L);
        ReflectionTestUtils.setField(service, "supportEmail", "support@example.com");
        ReflectionTestUtils.setField(service, "verificationResendCooldownSeconds", 60);
        ReflectionTestUtils.setField(service, "maxVerificationResendAttempts", 5);
        ReflectionTestUtils.setField(service, "login2FAExpiryMinutes", 15);
        ReflectionTestUtils.setField(service, "mobileLogin2FAExpiryMinutes", 10);
        ReflectionTestUtils.setField(service, "cookieSecure", false);
        ReflectionTestUtils.setField(service, "login2FABypassEmails", "person@example.com");

        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        when(suspensions.findByUserId(any())).thenReturn(Optional.empty());
        when(jwt.getAccessTokenExpirySeconds()).thenReturn(900L);
        when(jwt.hashToken(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
    }

    @Test
    void mobileBypassLoginReturnsBothCredentialsAndPersistsOnlyHash() {
        arrangeActiveLogin();
        when(jwt.generateAccessToken(userId, tenantId, "person@example.com", "TENANT_ADMIN"))
                .thenReturn("access-value");
        when(jwt.generateRefreshToken()).thenReturn("refresh-sentinel");
        when(jwt.hashToken("refresh-sentinel")).thenReturn("opaque-hash-value");

        MobileAuthResponse response = (MobileAuthResponse) service.mobileLogin(mobileLogin());

        assertEquals("access-value", response.getAccessToken());
        assertEquals("refresh-sentinel", response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());
        assertEquals(tenantId.toString(), response.getUser().getTenantId());

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertEquals("opaque-hash-value", saved.getValue().getTokenHash());
        assertFalse(saved.getValue().getTokenHash().contains("refresh-sentinel"));
        verify(events, never()).publishEvent(any());
    }

    @Test
    void browserAndMobileLoginsAppendIndependentSessions() {
        arrangeActiveLogin();
        when(jwt.generateAccessToken(any(), any(), anyString(), anyString())).thenReturn("access");
        when(jwt.generateRefreshToken()).thenReturn("refresh-one", "refresh-two");

        service.mobileLogin(mobileLogin());
        service.login(browserLogin(), new MockHttpServletResponse());

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals("hash:refresh-one", saved.getAllValues().get(0).getTokenHash());
        assertEquals("hash:refresh-two", saved.getAllValues().get(1).getTokenHash());
        verify(refreshTokens, never()).revokeAllByUserId(any());
    }

    @Test
    void standardMobileLoginCreatesBcryptOnlySixDigitChallengeWithTenMinuteExpiry() {
        ReflectionTestUtils.setField(service, "login2FABypassEmails", "");
        arrangeActiveLogin();
        when(loginStates.findByEmail("person@example.com")).thenReturn(Optional.empty());

        LoginStep1Response response = (LoginStep1Response) service.mobileLogin(mobileLogin());

        assertTrue(response.getRequires2FA());
        assertEquals("person@example.com", response.getEmail());
        ArgumentCaptor<Login2FAState> saved = ArgumentCaptor.forClass(Login2FAState.class);
        verify(loginStates).save(saved.capture());
        Login2FAState challenge = saved.getValue();
        assertEquals(Login2FAChallengeType.CODE, challenge.getChallengeType());
        assertTrue(challenge.getTokenHash().startsWith("$2"));
        assertTrue(challenge.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(9)));
        assertTrue(challenge.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(11)));
        assertEquals(0, challenge.getVerificationAttemptCount());
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        Login2FARequestedEvent codeEvent = (Login2FARequestedEvent) published.getValue();
        verify(events).publishEvent(any(ApplicationEvent.class));
        assertTrue(codeEvent.getVerificationSecret().matches("\\d{6}"));
        assertTrue(passwordEncoder.matches(codeEvent.getVerificationSecret(), challenge.getTokenHash()));
        assertFalse(challenge.getTokenHash().contains(codeEvent.getVerificationSecret()));
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void browserLoginStillCreatesSignedLinkChallengeWithFifteenMinuteExpiry() {
        ReflectionTestUtils.setField(service, "login2FABypassEmails", "");
        arrangeActiveLogin();
        when(loginStates.findByEmail("person@example.com")).thenReturn(Optional.empty());
        when(jwt.generateVerificationToken(userId, "person@example.com")).thenReturn("browser-link-token");
        when(jwt.hashToken("browser-link-token")).thenReturn("browser-link-hash");

        LoginStep1Response response = (LoginStep1Response) service.login(
                browserLogin(), new MockHttpServletResponse());

        assertTrue(response.getMessage().contains("verification link"));
        ArgumentCaptor<Login2FAState> saved = ArgumentCaptor.forClass(Login2FAState.class);
        verify(loginStates).save(saved.capture());
        assertEquals(Login2FAChallengeType.LINK, saved.getValue().getChallengeType());
        assertEquals("browser-link-hash", saved.getValue().getTokenHash());
        assertTrue(saved.getValue().getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(14)));
    }

    @Test
    void browserVerificationLinkStillIssuesCookieCredentials() {
        Claims claims = mock(Claims.class);
        when(claims.get("userId", String.class)).thenReturn(userId.toString());
        when(claims.getSubject()).thenReturn("person@example.com");
        when(jwt.validateVerificationToken("browser-link-token")).thenReturn(claims);
        when(jwt.hashToken("browser-link-token")).thenReturn("browser-link-hash");
        Login2FAState state = new Login2FAState();
        state.setId(UUID.randomUUID());
        state.setUserId(userId);
        state.setEmail("person@example.com");
        state.setChallengeType(Login2FAChallengeType.LINK);
        state.setTokenHash("browser-link-hash");
        state.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(loginStates.findByEmail("person@example.com")).thenReturn(Optional.of(state));
        when(loginStates.consumeIfActive(any(), any())).thenReturn(1);
        when(tenants.findUserById(userId)).thenReturn(activeUser("ACTIVE"));
        when(jwt.generateAccessToken(any(), any(), anyString(), anyString())).thenReturn("access");
        when(jwt.generateRefreshToken()).thenReturn("refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.verifyLogin2FA("browser-link-token", response);

        assertTrue(response.getHeader("Set-Cookie").contains("refresh_token=refresh"));
        verify(loginStates).consumeIfActive(org.mockito.ArgumentMatchers.eq(state.getId()), any());
    }

    @Test
    void inactiveUserCannotStartLogin() {
        when(tenants.authenticateUser("person@example.com", "password123"))
                .thenReturn(TenantUserResult.builder()
                        .userId(userId)
                        .tenantId(tenantId)
                        .email("person@example.com")
                        .status("INACTIVE")
                        .build());

        assertThrows(UnauthorizedException.class, () -> service.mobileLogin(mobileLogin()));
        verify(loginStates, never()).save(any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void mobileRefreshConsumesOnlySubmittedTokenAndAllowsRestrictedTenantStatus() {
        RefreshToken stored = storedToken(false, false, LocalDateTime.now().plusDays(1));
        when(refreshTokens.findByTokenHash("hash:old-refresh")).thenReturn(Optional.of(stored));
        when(refreshTokens.consumeActiveToken(any(), anyString(), any())).thenReturn(1);
        when(tenants.findUserById(userId)).thenReturn(activeUser("SUSPENDED"));
        when(jwt.generateAccessToken(any(), any(), anyString(), anyString())).thenReturn("new-access");
        when(jwt.generateRefreshToken()).thenReturn("new-refresh");

        MobileAuthResponse response = service.mobileRefreshToken("old-refresh");

        assertEquals("new-refresh", response.getRefreshToken());
        verify(refreshTokens).consumeActiveToken(
                org.mockito.ArgumentMatchers.eq(stored.getId()),
                org.mockito.ArgumentMatchers.eq("hash:old-refresh"),
                any());
        verify(refreshTokens, never()).revokeAllByUserId(any());
    }

    @Test
    void concurrentLoserCannotIssueReplacement() {
        RefreshToken stored = storedToken(false, false, LocalDateTime.now().plusDays(1));
        when(refreshTokens.findByTokenHash("hash:old-refresh")).thenReturn(Optional.of(stored));
        when(refreshTokens.consumeActiveToken(any(), anyString(), any())).thenReturn(0);
        when(tenants.findUserById(userId)).thenReturn(activeUser("ACTIVE"));

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> service.mobileRefreshToken("old-refresh"));

        assertEquals("Invalid refresh token", exception.getMessage());
        verify(jwt, never()).generateRefreshToken();
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void mobileRefreshUsesOneGenericErrorForUnknownExpiredRevokedAndTenantMismatch() {
        when(refreshTokens.findByTokenHash("hash:unknown")).thenReturn(Optional.empty());
        assertGenericRefreshFailure("unknown");

        when(refreshTokens.findByTokenHash("hash:expired"))
                .thenReturn(Optional.of(storedToken(false, false, LocalDateTime.now().minusSeconds(1))));
        assertGenericRefreshFailure("expired");

        when(refreshTokens.findByTokenHash("hash:revoked"))
                .thenReturn(Optional.of(storedToken(true, false, LocalDateTime.now().plusDays(1))));
        assertGenericRefreshFailure("revoked");

        RefreshToken mismatched = storedToken(false, false, LocalDateTime.now().plusDays(1));
        when(refreshTokens.findByTokenHash("hash:mismatch")).thenReturn(Optional.of(mismatched));
        when(tenants.findUserById(userId)).thenReturn(UserAuthDto.builder()
                .userId(userId)
                .tenantId(UUID.randomUUID())
                .status("ACTIVE")
                .build());
        assertGenericRefreshFailure("mismatch");

        RefreshToken deactivated = storedToken(false, false, LocalDateTime.now().plusDays(1));
        when(refreshTokens.findByTokenHash("hash:deactivated")).thenReturn(Optional.of(deactivated));
        when(tenants.findUserById(userId)).thenReturn(UserAuthDto.builder()
                .userId(userId)
                .tenantId(tenantId)
                .status("INACTIVE")
                .build());
        assertGenericRefreshFailure("deactivated");
    }

    @Test
    void browserRefreshRetainsDetailedErrorsAndCookiePersistence() {
        RefreshToken revoked = storedToken(true, true, LocalDateTime.now().plusDays(1));
        when(refreshTokens.findByTokenHash("hash:revoked-browser")).thenReturn(Optional.of(revoked));
        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> service.refreshToken("revoked-browser", mock(HttpServletResponse.class)));
        assertEquals("Refresh token revoked", exception.getMessage());

        RefreshToken stored = storedToken(false, true, LocalDateTime.now().plusDays(1));
        when(refreshTokens.findByTokenHash("hash:valid-browser")).thenReturn(Optional.of(stored));
        when(refreshTokens.consumeActiveToken(any(), anyString(), any())).thenReturn(1);
        when(tenants.findUserById(userId)).thenReturn(activeUser("EXPIRED"));
        when(jwt.generateAccessToken(any(), any(), anyString(), anyString())).thenReturn("access");
        when(jwt.generateRefreshToken()).thenReturn("rotated");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.refreshToken("valid-browser", response);

        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("refresh_token=rotated"));
        assertTrue(cookie.contains("Path=/api"));
        assertTrue(cookie.contains("Max-Age=2592000"));
    }

    @Test
    void logoutIsIdempotentAndTargetsOnlySubmittedHash() {
        service.logout("logout-sentinel");

        verify(refreshTokens).revokeByTokenHash("hash:logout-sentinel");
        verify(refreshTokens, never()).revokeAllByUserId(any());
        verify(refreshTokens, never()).findByTokenHash(anyString());
    }

    @Test
    void mobileTwoFactorVerificationAtomicallyConsumesCodeAndIssuesCredentials() {
        Login2FAState state = new Login2FAState();
        state.setId(UUID.randomUUID());
        state.setUserId(userId);
        state.setEmail("person@example.com");
        state.setChallengeType(Login2FAChallengeType.CODE);
        state.setTokenHash(passwordEncoder.encode("123456"));
        state.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        state.setAttemptCount(1);
        when(loginStates.findByEmail("person@example.com")).thenReturn(Optional.of(state));
        when(loginStates.consumeIfActive(any(), any())).thenReturn(1);
        when(tenants.findUserById(userId)).thenReturn(activeUser("INACTIVE"));
        when(jwt.generateAccessToken(any(), any(), anyString(), anyString())).thenReturn("access");
        when(jwt.generateRefreshToken()).thenReturn("refresh");

        MobileAuthResponse response = service.mobileVerifyLogin2FA("person@example.com", "123456");

        assertEquals("refresh", response.getRefreshToken());
        verify(loginStates).consumeIfActive(org.mockito.ArgumentMatchers.eq(state.getId()), any());
    }

    @Test
    void inactiveUserCannotCompleteTwoFactorVerification() {
        Login2FAState state = new Login2FAState();
        state.setId(UUID.randomUUID());
        state.setUserId(userId);
        state.setChallengeType(Login2FAChallengeType.CODE);
        state.setTokenHash(passwordEncoder.encode("123456"));
        state.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(loginStates.findByEmail("person@example.com")).thenReturn(Optional.of(state));
        when(tenants.findUserById(userId)).thenReturn(UserAuthDto.builder()
                .userId(userId)
                .tenantId(tenantId)
                .status("INACTIVE")
                .build());

        assertThrows(
                UnauthorizedException.class,
                () -> service.mobileVerifyLogin2FA("person@example.com", "123456"));
        assertFalse(state.isUsed());
        verify(loginStates, never()).consumeIfActive(any(), any());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void incorrectMobileCodeRecordsAttemptWithoutSuspendingOrRevokingSessions() {
        Login2FAState state = new Login2FAState();
        state.setId(UUID.randomUUID());
        state.setUserId(userId);
        state.setEmail("person@example.com");
        state.setChallengeType(Login2FAChallengeType.CODE);
        state.setTokenHash(passwordEncoder.encode("123456"));
        state.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(loginStates.findByEmail("person@example.com")).thenReturn(Optional.of(state));

        UnauthorizedException error = assertThrows(
                UnauthorizedException.class,
                () -> service.mobileVerifyLogin2FA("person@example.com", "000000"));

        assertEquals("Invalid or expired confirmation code", error.getMessage());
        verify(loginAttempts).recordIncorrectAttempt(
                org.mockito.ArgumentMatchers.eq(state.getId()), any(), org.mockito.ArgumentMatchers.eq(5));
        verify(tenants, never()).suspendUser(any());
        verify(refreshTokens, never()).revokeAllByUserId(any());
    }

    @Test
    void expiredMobileCodeIsRejectedGenerically() {
        Login2FAState state = new Login2FAState();
        state.setId(UUID.randomUUID());
        state.setUserId(userId);
        state.setEmail("person@example.com");
        state.setChallengeType(Login2FAChallengeType.CODE);
        state.setTokenHash(passwordEncoder.encode("123456"));
        state.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(loginStates.findByEmail("person@example.com")).thenReturn(Optional.of(state));

        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> service.mobileVerifyLogin2FA("person@example.com", "123456"));

        assertEquals("Invalid or expired confirmation code", error.getMessage());
        verify(loginAttempts, never()).recordIncorrectAttempt(any(), any(), any(Integer.class));
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void mobileResendReplacesCodeAndResetsIncorrectAttempts() {
        Login2FAState state = new Login2FAState();
        state.setId(UUID.randomUUID());
        state.setUserId(userId);
        state.setEmail("person@example.com");
        state.setChallengeType(Login2FAChallengeType.CODE);
        state.setTokenHash(passwordEncoder.encode("111111"));
        state.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        state.setAttemptCount(1);
        state.setVerificationAttemptCount(3);
        state.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        when(loginStates.findByEmail("person@example.com")).thenReturn(Optional.of(state));
        when(tenants.findUserById(userId)).thenReturn(activeUser("ACTIVE"));

        service.resendLogin2FA("person@example.com");

        assertEquals(Login2FAChallengeType.CODE, state.getChallengeType());
        assertEquals(0, state.getVerificationAttemptCount());
        assertFalse(passwordEncoder.matches("111111", state.getTokenHash()));
        assertTrue(state.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(9)));
        verify(loginStates).save(state);
    }

    @Test
    void concurrentCorrectCodeLoserCannotIssueCredentials() {
        Login2FAState state = new Login2FAState();
        state.setId(UUID.randomUUID());
        state.setUserId(userId);
        state.setEmail("person@example.com");
        state.setChallengeType(Login2FAChallengeType.CODE);
        state.setTokenHash(passwordEncoder.encode("123456"));
        state.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(loginStates.findByEmail("person@example.com")).thenReturn(Optional.of(state));
        when(loginStates.consumeIfActive(any(), any())).thenReturn(0);
        when(tenants.findUserById(userId)).thenReturn(activeUser("ACTIVE"));

        assertThrows(UnauthorizedException.class,
                () -> service.mobileVerifyLogin2FA("person@example.com", "123456"));
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void verificationAbuseSuspensionRevokesEverySession() {
        ReflectionTestUtils.setField(service, "login2FABypassEmails", "");
        Login2FAState state = new Login2FAState();
        state.setUserId(userId);
        state.setEmail("person@example.com");
        state.setAttemptCount(5);
        when(loginStates.findByEmail("person@example.com")).thenReturn(Optional.of(state));
        when(tenants.findUserById(userId)).thenReturn(activeUser("ACTIVE"));

        assertThrows(
                UnauthorizedException.class,
                () -> service.resendLogin2FA("person@example.com"));

        verify(tenants).suspendUser(userId);
        verify(refreshTokens).revokeAllByUserId(userId);
    }

    private void assertGenericRefreshFailure(String token) {
        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> service.mobileRefreshToken(token));
        assertEquals("Invalid refresh token", exception.getMessage());
        assertFalse(exception.getMessage().contains(token));
    }

    private void arrangeActiveLogin() {
        when(tenants.authenticateUser("person@example.com", "password123"))
                .thenReturn(TenantUserResult.builder()
                        .userId(userId)
                        .tenantId(tenantId)
                        .email("person@example.com")
                        .fullName("Person Name")
                        .role("TENANT_ADMIN")
                        .plan("PRO")
                        .status("ACTIVE")
                        .build());
        when(tenants.findUserById(userId)).thenReturn(activeUser("ACTIVE"));
    }

    private MobileLoginRequest mobileLogin() {
        MobileLoginRequest request = new MobileLoginRequest();
        request.setEmail("person@example.com");
        request.setPassword("password123");
        return request;
    }

    private LoginRequest browserLogin() {
        LoginRequest request = new LoginRequest();
        request.setEmail("person@example.com");
        request.setPassword("password123");
        request.setRememberMe(false);
        return request;
    }

    private UserAuthDto activeUser(String tenantStatus) {
        return UserAuthDto.builder()
                .userId(userId)
                .tenantId(tenantId)
                .email("person@example.com")
                .fullName("Person Name")
                .role("TENANT_ADMIN")
                .plan("PRO")
                .status("ACTIVE")
                .tenantStatus(tenantStatus)
                .build();
    }

    private RefreshToken storedToken(boolean revoked, boolean persistent, LocalDateTime expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTenantId(tenantId);
        token.setTokenHash("stored-hash");
        token.setRevoked(revoked);
        token.setPersistent(persistent);
        token.setExpiresAt(expiresAt);
        return token;
    }
}
