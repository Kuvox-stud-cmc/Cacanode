package com.cacanode.api.tenant.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PlatformStaffApi {
    SeedResult seedFirstAdministrator(String email, String fullName, String passwordHash);
    PageResult<StaffItem> staff(UUID actorTenantId, UUID actorId, ListQuery query);
    PageResult<InvitationItem> invitations(UUID actorTenantId, UUID actorId, ListQuery query);
    InvitationItem invite(UUID actorTenantId, UUID actorId, String email, String ip, String userAgent);
    InvitationItem resend(UUID actorTenantId, UUID actorId, UUID invitationId, String ip, String userAgent);
    void cancel(UUID actorTenantId, UUID actorId, UUID invitationId, String ip, String userAgent);
    StaffItem updateStatus(UUID actorTenantId, UUID actorId, UUID staffId, String status,
                           String ip, String userAgent);

    record ListQuery(int page, int size, String q, String status, String sort, String direction) {}
    record PageResult<T>(List<T> items, int page, int size, long total) {
        public PageResult { items = List.copyOf(items); }
    }
    record StaffItem(UUID id, String email, String name, String role, String status,
                     LocalDateTime createdAt, LocalDateTime lastLoginAt, boolean currentUser) {}
    record InvitationItem(UUID id, String email, String role, String status,
                          LocalDateTime createdAt, LocalDateTime expiresAt, LocalDateTime lastSentAt) {}
    record SeedResult(UUID tenantId, UUID userId, boolean created) {}
}
