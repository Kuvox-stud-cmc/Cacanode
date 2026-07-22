package com.cacanode.api.analytics.query;

import com.cacanode.api.chat.api.ChatApi;
import com.cacanode.api.document.api.DocumentApi;
import com.cacanode.api.support.api.SupportAnalyticsExportApi;
import com.cacanode.api.tenant.api.TenantAnalyticsExportApi;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsProjectionRebuildServiceTest {
    @Test
    void rebuildUsesOnlyOwnerExportsAndNeverStoresAssistantAnswerContent() {
        JdbcTemplate jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build());
        createProjectionTables(jdbc);
        TenantAnalyticsExportApi tenants = mock(TenantAnalyticsExportApi.class);
        DocumentApi documents = mock(DocumentApi.class);
        ChatApi chats = mock(ChatApi.class);
        SupportAnalyticsExportApi support = mock(SupportAnalyticsExportApi.class);
        LocalDateTime now = LocalDateTime.now();
        UUID tenantId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(tenants.tenants(0, 500)).thenReturn(new TenantAnalyticsExportApi.SnapshotPage<>(List.of(
                new TenantAnalyticsExportApi.TenantSnapshot(
                        tenantId, "Acme", "ACTIVE", "PRO", 10240, now, now)), false));
        when(tenants.users(0, 500)).thenReturn(new TenantAnalyticsExportApi.SnapshotPage<>(List.of(), false));
        when(tenants.invitations(0, 500)).thenReturn(new TenantAnalyticsExportApi.SnapshotPage<>(List.of(), false));
        when(documents.projectionSnapshots(0, 500)).thenReturn(new DocumentApi.ProjectionPage(List.of(), false));
        when(chats.projectionConversations(0, 500)).thenReturn(new ChatApi.ConversationPage(List.of(
                new ChatApi.ConversationSnapshot(conversationId, tenantId, "WIDGET", "OPEN", now, null, now)), false));
        when(chats.projectionMessages(0, 500)).thenReturn(new ChatApi.MessagePage(List.of(
                new ChatApi.MessageSnapshot(UUID.randomUUID(), conversationId, tenantId, "WIDGET",
                        "user", "How do I start?", null, 1, now),
                new ChatApi.MessageSnapshot(UUID.randomUUID(), conversationId, tenantId, "WIDGET",
                        "assistant", null, 1250L, 2, now.plusNanos(1))), false));
        when(support.projectionTickets(0, 500)).thenReturn(
                new SupportAnalyticsExportApi.TicketPage(List.of(), false));

        var result = new AnalyticsProjectionRebuildService(
                jdbc, tenants, documents, chats, support).rebuild();

        assertThat(result.tenants()).isEqualTo(1);
        assertThat(result.messages()).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_message_projection
                WHERE role = 'assistant' AND question_text IS NULL AND response_duration_ms = 1250
                """, Long.class)).isEqualTo(1L);
    }

    private void createProjectionTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE analytics_tenant_projection (tenant_id UUID PRIMARY KEY, name VARCHAR, status VARCHAR, plan VARCHAR, max_storage_mb BIGINT, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_user_projection (user_id UUID PRIMARY KEY, tenant_id UUID, status VARCHAR, role VARCHAR, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_invitation_projection (invitation_id UUID PRIMARY KEY, tenant_id UUID, status VARCHAR, created_at TIMESTAMP, expires_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_document_projection (document_id UUID PRIMARY KEY, tenant_id UUID, file_name VARCHAR, file_type VARCHAR, status VARCHAR, visibility VARCHAR, file_size_bytes BIGINT, created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_conversation_projection (conversation_id UUID PRIMARY KEY, tenant_id UUID, channel VARCHAR, status VARCHAR, created_at TIMESTAMP, closed_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_message_projection (message_id UUID PRIMARY KEY, conversation_id UUID, tenant_id UUID, channel VARCHAR, role VARCHAR, question_text VARCHAR, response_duration_ms BIGINT, sequence_number INT, created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_ticket_projection (ticket_id UUID PRIMARY KEY, tenant_id UUID, status VARCHAR, priority VARCHAR, created_at TIMESTAMP, resolved_at TIMESTAMP, updated_at TIMESTAMP)");
    }
}
