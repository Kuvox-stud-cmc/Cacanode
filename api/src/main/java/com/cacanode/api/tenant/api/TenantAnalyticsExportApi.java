package com.cacanode.api.tenant.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TenantAnalyticsExportApi {
    SnapshotPage<TenantSnapshot> tenants(int page, int size);
    SnapshotPage<UserSnapshot> users(int page, int size);
    SnapshotPage<InvitationSnapshot> invitations(int page, int size);

    record SnapshotPage<T>(List<T> items, boolean hasMore) {
        public SnapshotPage { items = List.copyOf(items); }
    }

    record TenantSnapshot(UUID id, String name, String status, String plan, long maxStorageMb,
                          LocalDateTime createdAt, LocalDateTime updatedAt, TenantKind kind) {
        public TenantSnapshot { kind = TenantKind.defaulted(kind); }
        public TenantSnapshot(UUID id, String name, String status, String plan, long maxStorageMb,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
            this(id, name, status, plan, maxStorageMb, createdAt, updatedAt, TenantKind.CUSTOMER);
        }
    }
    record UserSnapshot(UUID id, UUID tenantId, String status, String role,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {}
    record InvitationSnapshot(UUID id, UUID tenantId, String status, LocalDateTime createdAt,
                              LocalDateTime expiresAt, LocalDateTime updatedAt) {}
}
