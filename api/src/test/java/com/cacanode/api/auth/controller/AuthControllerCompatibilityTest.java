package com.cacanode.api.auth.controller;

import com.cacanode.api.auth.dto.response.AuthResponse;
import com.cacanode.api.auth.dto.response.LoginStep1Response;
import com.cacanode.api.auth.service.AuthService;
import com.cacanode.api.tenant.service.TenantUserManagementService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerCompatibilityTest {

    private AuthService authService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new AuthController(authService, mock(TenantUserManagementService.class)))
                .build();
    }

    @Test
    void browserLoginShapeAndLegacyAliasRemainAvailable() throws Exception {
        when(authService.login(any(), any())).thenReturn(LoginStep1Response.builder()
                .message("Check email")
                .email("person@example.com")
                .requires2FA(true)
                .build());

        String body = "{\"email\":\"person@example.com\",\"password\":\"password123\"}";
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires2FA").value(true));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires2FA").value(true));
    }

    @Test
    void browserRefreshStillUsesCookieAndRotatesCookie() throws Exception {
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(1);
            response.addHeader(HttpHeaders.SET_COOKIE,
                    "refresh_token=rotated; Path=/api; HttpOnly; SameSite=Strict");
            return authResponse();
        }).when(authService).refreshToken(any(), any());

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refresh_token=rotated")));
    }

    @Test
    void browserLogoutStillClearsCookie() throws Exception {
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(0);
            response.addHeader(HttpHeaders.SET_COOKIE,
                    "refresh_token=; Path=/api; Max-Age=0; HttpOnly; SameSite=Strict");
            return null;
        }).when(authService).clearRefreshTokenCookie(any());

        mvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "current")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    private AuthResponse authResponse() {
        return AuthResponse.builder()
                .accessToken("access-token")
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
