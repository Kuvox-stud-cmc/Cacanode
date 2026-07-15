package com.cacanode.api.tenant.api;

import java.util.UUID;

import com.cacanode.api.tenant.dto.UserAuthDto;

public interface TenantModuleApi {

    /**
     * Creates a new tenant and its first admin user.
     * Called by auth module during registration.
     **/
    TenantUserResult registerTenantWithAdmin(RegisterTenantCommand command);

    /**
     * Check use's email and password for login
     */
    TenantUserResult authenticateUser(String email, String password);

    /**
     * Finds a user by email for authentication
     * Called by auth module during login.
     */
    UserAuthDto findUserByEmail(String email);

    /**
     * Get user by userId for token refresh
     */
    UserAuthDto findUserById(UUID userId);

    /**
     * Checks if email already exists across all tenants.
     */
    boolean existsByEmail(String email);

    /**
     * Activates a user by setting their status to ACTIVE.
     * Called by auth module during email verification.
     *
     * @return UserAuthDto with updated user info
     */
    UserAuthDto activateUser(UUID userId);

    /**
     * Suspends a user by setting their status to SUSPENDED.
     * Called by auth module when verification resend limit exceeded.
     *
     * @param userId the user ID to suspend
     */
    void suspendUser(UUID userId);

    TenantEntitlements getEntitlements(UUID tenantId);

    TenantEntitlements lockEntitlements(UUID tenantId);

    void applyEntitlements(ApplyTenantEntitlementsCommand command);
}
