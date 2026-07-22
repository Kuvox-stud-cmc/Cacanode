package com.cacanode.api.analytics.query;

import com.cacanode.api.analytics.api.AnalyticsProjectionRebuildApi;
import com.cacanode.api.chat.api.ChatApi;
import com.cacanode.api.document.api.DocumentApi;
import com.cacanode.api.support.api.SupportAnalyticsExportApi;
import com.cacanode.api.tenant.api.TenantAnalyticsExportApi;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsProjectionRebuildService implements AnalyticsProjectionRebuildApi {
    private static final int PAGE_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final TenantAnalyticsExportApi tenantExport;
    private final DocumentApi documentApi;
    private final ChatApi chatApi;
    private final SupportAnalyticsExportApi supportExport;

    @Override
    @Transactional
    public RebuildResult rebuild() {
        clearProjections();
        long tenants = rebuildTenants();
        long users = rebuildUsers();
        long invitations = rebuildInvitations();
        long documents = rebuildDocuments();
        long conversations = rebuildConversations();
        long messages = rebuildMessages();
        long tickets = rebuildTickets();
        return new RebuildResult(tenants, users, invitations, documents,
                conversations, messages, tickets);
    }

    private void clearProjections() {
        jdbcTemplate.update("DELETE FROM analytics_message_projection");
        jdbcTemplate.update("DELETE FROM analytics_conversation_projection");
        jdbcTemplate.update("DELETE FROM analytics_ticket_projection");
        jdbcTemplate.update("DELETE FROM analytics_document_projection");
        jdbcTemplate.update("DELETE FROM analytics_invitation_projection");
        jdbcTemplate.update("DELETE FROM analytics_user_projection");
        jdbcTemplate.update("DELETE FROM analytics_tenant_projection");
    }

    private long rebuildTenants() {
        long count = 0;
        for (int page = 0; ; page++) {
            var snapshot = tenantExport.tenants(page, PAGE_SIZE);
            for (var item : snapshot.items()) {
                jdbcTemplate.update("""
                        INSERT INTO analytics_tenant_projection
                        (tenant_id, name, status, plan, max_storage_mb, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, item.id(), item.name(), item.status(), item.plan(), item.maxStorageMb(),
                        item.createdAt(), item.updatedAt());
                count++;
            }
            if (!snapshot.hasMore()) return count;
        }
    }

    private long rebuildUsers() {
        long count = 0;
        for (int page = 0; ; page++) {
            var snapshot = tenantExport.users(page, PAGE_SIZE);
            for (var item : snapshot.items()) {
                jdbcTemplate.update("""
                        INSERT INTO analytics_user_projection
                        (user_id, tenant_id, status, role, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, item.id(), item.tenantId(), item.status(), item.role(),
                        item.createdAt(), item.updatedAt());
                count++;
            }
            if (!snapshot.hasMore()) return count;
        }
    }

    private long rebuildInvitations() {
        long count = 0;
        for (int page = 0; ; page++) {
            var snapshot = tenantExport.invitations(page, PAGE_SIZE);
            for (var item : snapshot.items()) {
                jdbcTemplate.update("""
                        INSERT INTO analytics_invitation_projection
                        (invitation_id, tenant_id, status, created_at, expires_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, item.id(), item.tenantId(), item.status(), item.createdAt(),
                        item.expiresAt(), item.updatedAt());
                count++;
            }
            if (!snapshot.hasMore()) return count;
        }
    }

    private long rebuildDocuments() {
        long count = 0;
        for (int page = 0; ; page++) {
            var snapshot = documentApi.projectionSnapshots(page, PAGE_SIZE);
            for (var item : snapshot.items()) {
                jdbcTemplate.update("""
                        INSERT INTO analytics_document_projection
                        (document_id, tenant_id, file_name, file_type, status, visibility,
                         file_size_bytes, created_at, updated_at, deleted_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                        """, item.id(), item.tenantId(), item.fileName(), item.fileType(), item.status(),
                        item.visibility(), item.fileSizeBytes(), item.createdAt(), item.updatedAt());
                count++;
            }
            if (!snapshot.hasMore()) return count;
        }
    }

    private long rebuildConversations() {
        long count = 0;
        for (int page = 0; ; page++) {
            var snapshot = chatApi.projectionConversations(page, PAGE_SIZE);
            for (var item : snapshot.items()) {
                jdbcTemplate.update("""
                        INSERT INTO analytics_conversation_projection
                        (conversation_id, tenant_id, channel, status, created_at, closed_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, item.id(), item.tenantId(), item.channel(), item.status(), item.createdAt(),
                        item.closedAt(), item.updatedAt());
                count++;
            }
            if (!snapshot.hasMore()) return count;
        }
    }

    private long rebuildMessages() {
        long count = 0;
        for (int page = 0; ; page++) {
            var snapshot = chatApi.projectionMessages(page, PAGE_SIZE);
            for (var item : snapshot.items()) {
                jdbcTemplate.update("""
                        INSERT INTO analytics_message_projection
                        (message_id, conversation_id, tenant_id, channel, role, question_text,
                         response_duration_ms, sequence_number, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, item.id(), item.conversationId(), item.tenantId(), item.channel(), item.role(),
                        item.questionText(), item.responseDurationMs(), item.sequenceNumber(), item.createdAt());
                count++;
            }
            if (!snapshot.hasMore()) return count;
        }
    }

    private long rebuildTickets() {
        long count = 0;
        for (int page = 0; ; page++) {
            var snapshot = supportExport.projectionTickets(page, PAGE_SIZE);
            for (var item : snapshot.items()) {
                jdbcTemplate.update("""
                        INSERT INTO analytics_ticket_projection
                        (ticket_id, tenant_id, status, priority, created_at, resolved_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, item.id(), item.tenantId(), item.status(), item.priority(), item.createdAt(),
                        item.resolvedAt(), item.updatedAt());
                count++;
            }
            if (!snapshot.hasMore()) return count;
        }
    }
}
