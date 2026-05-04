package com.cacanode.api.tenant.api;

import com.cacanode.api.tenant.dto.RegisterTenantCommand;
import com.cacanode.api.tenant.dto.TenantUserResult;
import com.cacanode.api.tenant.dto.UserAuthDto;

import java.util.UUID;

public interface TenantModuleApi {

    /**
     * Creates a new tenant and its first admin user.
     * Called by auth module during registration.
     **/
    TenantUserResult registerTenantWithAdmin(RegisterTenantCommand command);

    /**
     * Finds a user by email for authentication
     * Called by auth module during login.
     * */
    UserAuthDto findUserByEmail(String email);

    /**
     * Checks if email already exists across all tenants.
     * */
    boolean existsByEmail(String email);
}
