package com.cacanode.api.auth.service;

import java.util.UUID;
import java.util.function.Function;

public interface JwtService {

    public String generateAccessToken(UUID userId, UUID tenantId, String email, String role);

    public String generateRefreshToken();

    public String hashToken(String token);

    public long getAccessTokenExpirySeconds();

    public String extractEmail(String token);

    public String extractTenantId(String token);

    public String extractUserId(String token);

    public String extractRole(String token);

    public <T> T extractClaim(String token, Function<io.jsonwebtoken.Claims, T> claimsResolver);

}
