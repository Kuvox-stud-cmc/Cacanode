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
     * */
    UserAuthDto findUserByEmail(String email);

    /**
     * Get user by userId for token refresh
     * */
    UserAuthDto findUserById(UUID userId);

    /**
     * Checks if email already exists across all tenants.
     * */
    boolean existsByEmail(String email);
}
