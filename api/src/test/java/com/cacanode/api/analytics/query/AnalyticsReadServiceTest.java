package com.cacanode.api.analytics.query;

import com.cacanode.api.analytics.api.AnalyticsDtos;
import com.cacanode.api.analytics.query.AnalyticsReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsReadServiceTest {
    private JdbcTemplate jdbc;
    private AnalyticsReadService service;
    private UUID tenantId;
    private UUID otherTenantId;
    private Map<UUID, String> sessionChannels;
    private Map<UUID, LocalDateTime> lastUserMessageAt;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build());
        service = new AnalyticsReadService(jdbc);
        tenantId = UUID.randomUUID();
        otherTenantId = UUID.randomUUID();
        sessionChannels = new HashMap<>();
        lastUserMessageAt = new HashMap<>();
        jdbc.execute("CREATE TABLE analytics_tenant_projection (tenant_id UUID PRIMARY KEY, name VARCHAR(255), status VARCHAR(50), plan VARCHAR(50), max_storage_mb BIGINT NOT NULL, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_user_projection (user_id UUID PRIMARY KEY, tenant_id UUID NOT NULL, status VARCHAR(50) NOT NULL, role VARCHAR(50), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_invitation_projection (invitation_id UUID PRIMARY KEY, tenant_id UUID NOT NULL, status VARCHAR(50), created_at TIMESTAMP, expires_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_document_projection (document_id UUID PRIMARY KEY, tenant_id UUID NOT NULL, file_name VARCHAR(500), file_type VARCHAR(50), status VARCHAR(50), visibility VARCHAR(50), file_size_bytes BIGINT, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP, deleted_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_conversation_projection (conversation_id UUID PRIMARY KEY, tenant_id UUID NOT NULL, channel VARCHAR(50) NOT NULL, status VARCHAR(50) NOT NULL, created_at TIMESTAMP NOT NULL, closed_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE analytics_message_projection (message_id UUID PRIMARY KEY, conversation_id UUID NOT NULL, tenant_id UUID NOT NULL, channel VARCHAR(50) NOT NULL, role VARCHAR(50) NOT NULL, question_text VARCHAR(2000), response_duration_ms BIGINT, sequence_number INT NOT NULL, created_at TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE analytics_ticket_projection (ticket_id UUID PRIMARY KEY, tenant_id UUID NOT NULL, status VARCHAR(50) NOT NULL, priority VARCHAR(50), created_at TIMESTAMP NOT NULL, resolved_at TIMESTAMP, updated_at TIMESTAMP)");
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO analytics_tenant_projection VALUES (?, 'Tenant', 'ACTIVE', 'TRIAL', ?, ?, ?)", tenantId, 10L, now, now);
        jdbc.update("INSERT INTO analytics_tenant_projection VALUES (?, 'Other', 'ACTIVE', 'TRIAL', ?, ?, ?)", otherTenantId, 99L, now, now);
    }

    @Test
    void dashboardIsTenantScopedAndUsesCalendarComparisons() {
        LocalDate today = LocalDate.now(Clock.systemUTC());
        LocalDateTime thisMonth = today.withDayOfMonth(1).atStartOfDay().plusHours(1);
        LocalDateTime previousMonth = today.withDayOfMonth(1).minusMonths(1).atStartOfDay().plusHours(1);
        insertDocument(tenantId, "tenant.pdf", 2_048, today.atTime(10, 0));
        insertDocument(otherTenantId, "other.pdf", 9_999, today.atTime(11, 0));
        jdbc.update("INSERT INTO analytics_user_projection VALUES (?, ?, 'ACTIVE', 'USER', ?, ?)", UUID.randomUUID(), tenantId, today.atStartOfDay(), today.atStartOfDay());
        jdbc.update("INSERT INTO analytics_user_projection VALUES (?, ?, 'INACTIVE', 'USER', ?, ?)", UUID.randomUUID(), tenantId, today.atStartOfDay(), today.atStartOfDay());
        UUID session = insertSession(tenantId, "WIDGET", "OPEN", thisMonth);
        insertMessage(tenantId, session, "user", "current", 1, thisMonth);
        insertMessage(tenantId, session, "user", "previous", 2, previousMonth);
        UUID otherSession = insertSession(otherTenantId, "WIDGET", "OPEN", thisMonth);
        insertMessage(otherTenantId, otherSession, "user", "other", 1, thisMonth);

        AnalyticsDtos.DashboardSummary result = service.dashboardSummary(tenantId);

        assertThat(result.totalDocuments()).isEqualTo(1);
        assertThat(result.storedDocumentBytes()).isEqualTo(2_048);
        assertThat(result.storageLimitBytes()).isEqualTo(10L * 1024 * 1024);
        assertThat(result.userMessagesThisMonth()).isEqualTo(1);
        assertThat(result.userMessagesPreviousMonth()).isEqualTo(1);
        assertThat(result.activeUsers()).isEqualTo(1);
        assertThat(result.recentDocuments()).extracting(AnalyticsDtos.RecentDocument::fileName).containsExactly("tenant.pdf");
    }

    @Test
    void dashboardToleratesAProjectionLagForTheTenantRow() {
        jdbc.update("DELETE FROM analytics_tenant_projection WHERE tenant_id = ?", tenantId);

        AnalyticsDtos.DashboardSummary result = service.dashboardSummary(tenantId);

        assertThat(result.storageLimitBytes()).isZero();
        assertThat(result.totalDocuments()).isZero();
        assertThat(result.recentDocuments()).isEmpty();
    }

    @Test
    void analyticsFiltersChannelsAndCalculatesMetrics() {
        LocalDate endDate = LocalDate.now(Clock.systemUTC()).plusDays(1);
        LocalDateTime currentStart = endDate.minusDays(7).atStartOfDay();
        LocalDateTime previousStart = endDate.minusDays(14).atStartOfDay();

        UUID customer = insertSession(tenantId, "WIDGET", "CLOSED", currentStart.plusHours(1));
        UUID employee = insertSession(tenantId, "EMPLOYEE_PLAYGROUND", "OPEN", currentStart.plusHours(2));
        UUID previous = insertSession(tenantId, "CUSTOM_API", "OPEN", previousStart.plusHours(1));
        UUID other = insertSession(otherTenantId, "WIDGET", "CLOSED", currentStart.plusHours(1));

        insertMessage(tenantId, customer, "user", "  How   do I? ", 1, currentStart.plusHours(3));
        insertMessage(tenantId, customer, "assistant", "Answer", 2, currentStart.plusHours(3).plusSeconds(2));
        insertMessage(tenantId, customer, "user", "how do i?", 3, currentStart.plusHours(4));
        insertMessage(tenantId, customer, "assistant", "Answer", 4, currentStart.plusHours(4).plusSeconds(4));
        insertMessage(tenantId, employee, "user", "Employee question", 1, currentStart.plusHours(3));
        insertMessage(tenantId, employee, "assistant", "Answer", 2, currentStart.plusHours(3).plusSeconds(1));
        insertMessage(tenantId, previous, "user", "Previous", 1, previousStart.plusHours(2));
        insertMessage(tenantId, previous, "assistant", "Answer", 2, previousStart.plusHours(2).plusSeconds(1));
        insertMessage(otherTenantId, other, "user", "Other tenant", 1, currentStart.plusHours(2));

        insertTicket(tenantId, "RESOLVED", currentStart.plusHours(1));
        insertTicket(tenantId, "OPEN", currentStart.plusHours(2));
        insertTicket(tenantId, "CLOSED", previousStart.plusHours(2));

        AnalyticsDtos.AnalyticsResponse customerResult = service.analytics(tenantId, AnalyticsDtos.AnalyticsScope.CUSTOMER, 7);
        AnalyticsDtos.AnalyticsResponse employeeResult = service.analytics(tenantId, AnalyticsDtos.AnalyticsScope.EMPLOYEE, 7);
        AnalyticsDtos.AnalyticsResponse allResult = service.analytics(tenantId, AnalyticsDtos.AnalyticsScope.ALL, 7);

        assertThat(customerResult.sessions().value()).isEqualTo(1);
        assertThat(customerResult.sessions().previousValue()).isEqualTo(1);
        assertThat(customerResult.userMessages().value()).isEqualTo(2);
        assertThat(customerResult.averageAssistantResponseTime().milliseconds()).isEqualTo(3_000);
        assertThat(customerResult.averageAssistantResponseTime().previousMilliseconds()).isEqualTo(1_000);
        assertThat(customerResult.closedSessionRate().percentage()).isEqualTo(100);
        assertThat(customerResult.resolvedTicketRate().percentage()).isEqualTo(50);
        assertThat(customerResult.resolvedTicketRate().percentagePointChange()).isEqualTo(-50);
        assertThat(customerResult.dailyMessageVolume()).hasSize(7);
        assertThat(customerResult.dailyMessageVolume()).extracting(AnalyticsDtos.DailyVolume::count).contains(0L);
        assertThat(customerResult.popularQuestions()).containsExactly(new AnalyticsDtos.PopularQuestion("How do I?", 2));
        assertThat(employeeResult.sessions().value()).isEqualTo(1);
        assertThat(employeeResult.userMessages().value()).isEqualTo(1);
        assertThat(employeeResult.resolvedTicketRate()).isNull();
        assertThat(allResult.sessions().value()).isEqualTo(2);
        assertThat(allResult.userMessages().value()).isEqualTo(3);
    }

    @Test
    void periodsHaveExactLengthsAndZeroDenominatorsStayFinite() {
        for (int days : new int[]{7, 30, 90}) {
            AnalyticsDtos.AnalyticsResponse result = service.analytics(tenantId, AnalyticsDtos.AnalyticsScope.ALL, days);
            assertThat(result.dailyMessageVolume()).hasSize(days);
            assertThat(result.sessions().percentageChange()).isZero();
            assertThat(result.averageAssistantResponseTime().percentageChange()).isZero();
            assertThat(result.closedSessionRate().percentagePointChange()).isZero();
        }
    }

    @Test
    void periodBoundariesAreInclusiveAtStartAndExclusiveAtEnd() {
        LocalDateTime end = LocalDate.now(Clock.systemUTC()).plusDays(1).atStartOfDay();
        LocalDateTime currentStart = end.minusDays(7);
        LocalDateTime previousStart = currentStart.minusDays(7);
        insertSession(tenantId, "WIDGET", "OPEN", previousStart.minusSeconds(1));
        insertSession(tenantId, "WIDGET", "OPEN", previousStart);
        insertSession(tenantId, "WIDGET", "OPEN", currentStart);
        insertSession(tenantId, "WIDGET", "OPEN", end.minusSeconds(1));
        insertSession(tenantId, "WIDGET", "OPEN", end);

        AnalyticsDtos.AnalyticsResponse result = service.analytics(tenantId, AnalyticsDtos.AnalyticsScope.CUSTOMER, 7);

        assertThat(result.sessions().value()).isEqualTo(2);
        assertThat(result.sessions().previousValue()).isEqualTo(1);
    }

    private UUID insertSession(UUID tenant, String channel, String status, LocalDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO analytics_conversation_projection VALUES (?, ?, ?, ?, ?, NULL, ?)", id, tenant, channel, status, createdAt, createdAt);
        sessionChannels.put(id, channel);
        return id;
    }

    private void insertMessage(UUID tenant, UUID session, String role, String content, int sequence, LocalDateTime createdAt) {
        Long responseDuration = null;
        String question = null;
        if ("user".equals(role)) {
            question = content;
            lastUserMessageAt.put(session, createdAt);
        } else if ("assistant".equals(role) && lastUserMessageAt.containsKey(session)) {
            responseDuration = Duration.between(lastUserMessageAt.get(session), createdAt).toMillis();
        }
        jdbc.update("INSERT INTO analytics_message_projection VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), session, tenant, sessionChannels.get(session), role,
                question, responseDuration, sequence, createdAt);
    }

    private void insertDocument(UUID tenant, String name, long bytes, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO analytics_document_projection VALUES (?, ?, ?, 'PDF', 'COMPLETED', 'PRIVATE', ?, ?, ?, NULL)",
                UUID.randomUUID(), tenant, name, bytes, createdAt, createdAt);
    }

    private void insertTicket(UUID tenant, String status, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO analytics_ticket_projection VALUES (?, ?, ?, 'NORMAL', ?, NULL, ?)",
                UUID.randomUUID(), tenant, status, createdAt, createdAt);
    }
}
