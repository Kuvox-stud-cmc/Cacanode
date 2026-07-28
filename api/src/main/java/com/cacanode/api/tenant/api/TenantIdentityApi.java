package com.cacanode.api.tenant.api;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

public interface TenantIdentityApi {
    TenantUserResult registerTenantWithAdmin(RegisterTenantCommand command);

    TenantUserResult authenticateUser(String email, String password);

    UserAuthDto findUserByEmail(String email);

    UserAuthDto findUserById(UUID userId);

    boolean existsByEmail(String email);

    UserAuthDto activateUser(UUID userId);

    void suspendUser(UUID userId);

    TenantSnapshot getTenant(UUID tenantId);

    UserSnapshot requireUser(UUID tenantId, UUID userId);

    List<UserSnapshot> listUsers(UUID tenantId);

    InvitationSnapshot validateInvitation(String rawToken);

    AcceptedUserSnapshot acceptInvitation(
            String rawToken, String fullName, String passwordHash);

    long memberUsage(UUID tenantId, LocalDateTime now);

    record TenantSnapshot(UUID id, String name, TenantKind kind) {
        public TenantSnapshot {
            kind = TenantKind.defaulted(kind);
        }

        public TenantSnapshot(UUID id, String name) {
            this(id, name, TenantKind.CUSTOMER);
        }
    }

    record UserSnapshot(UUID id, UUID tenantId, String fullName, String email, String role, String status,
                        TenantKind tenantKind) {
        public UserSnapshot {
            tenantKind = TenantKind.defaulted(tenantKind);
        }
        public UserSnapshot(UUID id, UUID tenantId, String fullName, String email, String role, String status) {
            this(id, tenantId, fullName, email, role, status, TenantKind.CUSTOMER);
        }
    }

    record InvitationSnapshot(String email, String tenantName, String role, LocalDateTime expiresAt) {
    }

    record AcceptedUserSnapshot(
            UUID userId,
            UUID tenantId,
            String email,
            String fullName,
            String role,
            String status,
            String plan,
            String tenantStatus,
            String passwordHash,
            TenantKind tenantKind
    ) {
        public AcceptedUserSnapshot {
            tenantKind = TenantKind.defaulted(tenantKind);
        }
    }
}
