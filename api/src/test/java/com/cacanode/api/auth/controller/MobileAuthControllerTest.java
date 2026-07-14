package com.cacanode.api.auth.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.cacanode.api.auth.dto.response.AuthResponse;
import com.cacanode.api.auth.dto.response.LoginStep1Response;
import com.cacanode.api.auth.dto.response.MobileAuthResponse;
import com.cacanode.api.auth.service.AuthService;
import com.cacanode.api.common.exception.GlobalExceptionHandler;
import com.cacanode.api.common.exception.custom.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MobileAuthControllerTest {

    private AuthService authService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new MobileAuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginReturnsTwoFactorStepWithoutCookie() throws Exception {
        when(authService.mobileLogin(any())).thenReturn(LoginStep1Response.builder()
                .message("Check email")
                .email("person@example.com")
                .requires2FA(true)
                .build());

        mvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"person@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires2FA").value(true))
                .andExpect(jsonPath("$.email").value("person@example.com"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginReturnsNativeCredentialShapeWithoutCookie() throws Exception {
        when(authService.mobileLogin(any())).thenReturn(credentials());

        mvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"person@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("person@example.com"))
                .andExpect(jsonPath("$.user.role").value("TENANT_ADMIN"))
                .andExpect(jsonPath("$.user.plan").value("PRO"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void verificationAndRefreshNeverSetCookies() throws Exception {
        when(authService.mobileVerifyLogin2FA("person@example.com", "123456")).thenReturn(credentials());
        when(authService.mobileRefreshToken("refresh-token")).thenReturn(credentials());

        mvc.perform(post("/api/v1/auth/mobile/verify-login-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        mvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void blankNativeRequestFieldsAreValidationErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/mobile/verify-login-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"code\":\" \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/mobile/verify-login-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"code\":\"12345a\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\" \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/mobile/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshUsesGenericUnauthorizedResponse() throws Exception {
        when(authService.mobileRefreshToken(anyString()))
                .thenThrow(new UnauthorizedException("Invalid refresh token"));

        mvc.perform(post("/api/v1/auth/mobile/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-sentinel\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("refresh-sentinel"))));
    }

    @Test
    void logoutIsIdempotentNoContentAndDoesNotClearCookies() throws Exception {
        doNothing().when(authService).logout(anyString());

        mvc.perform(post("/api/v1/auth/mobile/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"unknown-token\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void legacyMobileAliasDoesNotExist() throws Exception {
        MockMvc mappingOnlyMvc = MockMvcBuilders
                .standaloneSetup(new MobileAuthController(authService))
                .build();

        mappingOnlyMvc.perform(post("/api/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"person@example.com","password":"password123"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationLogsContainNeitherRequestBodyNorRejectedValues() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mvc.perform(post("/api/v1/auth/mobile/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"refresh-sentinel","password":"password123"}
                                    """))
                    .andExpect(status().isBadRequest());

            String messages = appender.list.stream()
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + right);
            assertFalse(messages.contains("refresh-sentinel"));
            assertFalse(messages.contains("password123"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private MobileAuthResponse credentials() {
        return MobileAuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(AuthResponse.UserInfo.builder()
                        .userId(UUID.randomUUID().toString())
                        .tenantId(UUID.randomUUID().toString())
                        .email("person@example.com")
                        .fullName("Person Name")
                        .role("TENANT_ADMIN")
                        .plan("PRO")
                        .build())
                .build();
    }
}
