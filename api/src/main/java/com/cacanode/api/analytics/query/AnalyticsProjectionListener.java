package com.cacanode.api.analytics.query;

import com.cacanode.api.auth.api.event.UserRegisteredEvent;
import com.cacanode.api.chat.api.event.ConversationClosedEvent;
import com.cacanode.api.chat.api.event.ConversationStartedEvent;
import com.cacanode.api.chat.api.event.MessageRecordedEvent;
import com.cacanode.api.chat.api.event.ConversationProjectionEvent;
import com.cacanode.api.common.event.durable.ModuleEventInboxService;
import com.cacanode.api.document.api.event.DocumentProjectionEvent;
import com.cacanode.api.support.api.event.TicketCreatedEvent;
import com.cacanode.api.support.api.event.TicketStatusChangedEvent;
import com.cacanode.api.tenant.api.event.TenantCreatedEvent;
import com.cacanode.api.tenant.api.event.UserInvitedEvent;
import com.cacanode.api.tenant.api.event.TenantProjectionChangedEvent;
import com.cacanode.api.tenant.api.event.UserProjectionChangedEvent;
import com.cacanode.api.tenant.api.event.InvitationProjectionChangedEvent;
import com.cacanode.api.recruitment.api.event.RecruitmentJobProjectionChangedEvent;
import com.cacanode.api.recruitment.api.event.RecruitmentApplicationProjectionChangedEvent;
import com.cacanode.api.recruitment.api.event.RecruitmentInterviewProjectionChangedEvent;
import com.cacanode.api.recruitment.api.event.RecruitmentErasureCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AnalyticsProjectionListener {
    private final JdbcTemplate jdbcTemplate;
    private final ModuleEventInboxService inboxService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tenantCreated(TenantCreatedEvent event) {
        if (!inboxService.claim("analytics.tenant")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_tenant_projection
                    (tenant_id, name, status, plan, max_storage_mb, created_at, updated_at, tenant_kind)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id) DO UPDATE SET
                    name = EXCLUDED.name, status = EXCLUDED.status, plan = EXCLUDED.plan,
                    max_storage_mb = EXCLUDED.max_storage_mb, updated_at = EXCLUDED.updated_at,
                    tenant_kind = EXCLUDED.tenant_kind
                """, event.tenantId(), event.name(), event.status(), event.plan(),
                event.maxStorageMb(), event.createdAt(), event.createdAt(), event.kind().name());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void userRegistered(UserRegisteredEvent event) {
        if (!inboxService.claim("analytics.user")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_user_projection
                    (user_id, tenant_id, status, role, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    status = EXCLUDED.status, role = EXCLUDED.role, updated_at = EXCLUDED.updated_at
                """, event.userId(), event.tenantId(), event.status(), event.role(),
                event.createdAt(), event.createdAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void userInvited(UserInvitedEvent event) {
        if (!inboxService.claim("analytics.invitation")) return;
        if (event.invitationId() == null) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_invitation_projection
                    (invitation_id, tenant_id, status, created_at, expires_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (invitation_id) DO UPDATE SET
                    status = EXCLUDED.status, expires_at = EXCLUDED.expires_at,
                    updated_at = EXCLUDED.updated_at
                """, event.invitationId(), event.tenantId(), event.status(), event.createdAt(),
                event.expiresAt(), event.createdAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tenantChanged(TenantProjectionChangedEvent event) {
        if (!inboxService.claim("analytics.tenant-changed")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_tenant_projection
                    (tenant_id, name, status, plan, max_storage_mb, created_at, updated_at, tenant_kind)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id) DO UPDATE SET
                    name = EXCLUDED.name, status = EXCLUDED.status, plan = EXCLUDED.plan,
                    max_storage_mb = EXCLUDED.max_storage_mb, updated_at = EXCLUDED.updated_at,
                    tenant_kind = EXCLUDED.tenant_kind
                """, event.tenantId(), event.name(), event.status(), event.plan(), event.maxStorageMb(),
                event.createdAt(), event.updatedAt(), event.kind().name());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void userChanged(UserProjectionChangedEvent event) {
        if (!inboxService.claim("analytics.user-changed")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_user_projection
                    (user_id, tenant_id, status, role, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    status = EXCLUDED.status, role = EXCLUDED.role, updated_at = EXCLUDED.updated_at
                """, event.userId(), event.tenantId(), event.status(), event.role(),
                event.createdAt(), event.updatedAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void invitationChanged(InvitationProjectionChangedEvent event) {
        if (!inboxService.claim("analytics.invitation-changed")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_invitation_projection
                    (invitation_id, tenant_id, status, created_at, expires_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (invitation_id) DO UPDATE SET
                    status = EXCLUDED.status, expires_at = EXCLUDED.expires_at,
                    updated_at = EXCLUDED.updated_at
                """, event.invitationId(), event.tenantId(), event.status(), event.createdAt(),
                event.expiresAt(), event.updatedAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void conversationStarted(ConversationStartedEvent event) {
        if (!inboxService.claim("analytics.conversation-started")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_conversation_projection
                    (conversation_id, tenant_id, channel, status, created_at, closed_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, ?)
                ON CONFLICT (conversation_id) DO UPDATE SET
                    channel = EXCLUDED.channel, status = EXCLUDED.status, updated_at = EXCLUDED.updated_at
                """, event.conversationId(), event.tenantId(), event.channel(), event.status(),
                event.createdAt(), event.createdAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void conversationClosed(ConversationClosedEvent event) {
        if (!inboxService.claim("analytics.conversation-closed")) return;
        jdbcTemplate.update("""
                UPDATE analytics_conversation_projection
                SET status = 'CLOSED', closed_at = ?, updated_at = ?
                WHERE conversation_id = ? AND tenant_id = ?
                """, event.closedAt(), event.closedAt(), event.conversationId(), event.tenantId());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void conversationProjected(ConversationProjectionEvent event) {
        if (!inboxService.claim("analytics.conversation")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_conversation_projection
                    (conversation_id, tenant_id, channel, status, created_at, closed_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (conversation_id) DO UPDATE SET
                    channel = EXCLUDED.channel, status = EXCLUDED.status,
                    closed_at = EXCLUDED.closed_at, updated_at = EXCLUDED.updated_at
                """, event.conversationId(), event.tenantId(), event.channel(), event.status(),
                event.createdAt(), event.closedAt(), event.updatedAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void messageRecorded(MessageRecordedEvent event) {
        if (!inboxService.claim("analytics.message")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_message_projection
                    (message_id, conversation_id, tenant_id, channel, role, question_text,
                     response_duration_ms, sequence_number, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (message_id) DO NOTHING
                """, event.messageId(), event.conversationId(), event.tenantId(), event.channel(),
                event.role(), event.questionText(), event.responseDurationMs(),
                event.sequenceNumber(), event.createdAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void documentChanged(DocumentProjectionEvent event) {
        if (!inboxService.claim("analytics.document")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_document_projection
                    (document_id, tenant_id, file_name, file_type, status, visibility,
                     file_size_bytes, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (document_id) DO UPDATE SET
                    file_name = EXCLUDED.file_name, file_type = EXCLUDED.file_type,
                    status = EXCLUDED.status, visibility = EXCLUDED.visibility,
                    file_size_bytes = EXCLUDED.file_size_bytes, updated_at = EXCLUDED.updated_at,
                    deleted_at = EXCLUDED.deleted_at
                """, event.documentId(), event.tenantId(), event.fileName(), event.fileType(),
                event.status(), event.visibility(), event.fileSizeBytes(), event.createdAt(),
                event.updatedAt(), event.deletedAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ticketCreated(TicketCreatedEvent event) {
        if (!inboxService.claim("analytics.ticket-created")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_ticket_projection
                    (ticket_id, tenant_id, status, priority, created_at, resolved_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, ?)
                ON CONFLICT (ticket_id) DO UPDATE SET
                    status = EXCLUDED.status, priority = EXCLUDED.priority,
                    updated_at = EXCLUDED.updated_at
                """, event.ticketId(), event.tenantId(), event.status(), event.priority(),
                event.createdAt(), event.updatedAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ticketStatusChanged(TicketStatusChangedEvent event) {
        if (!inboxService.claim("analytics.ticket-status")) return;
        LocalDateTime resolvedAt = event.resolvedAt();
        jdbcTemplate.update("""
                UPDATE analytics_ticket_projection
                SET status = ?, resolved_at = ?, updated_at = ?
                WHERE ticket_id = ? AND tenant_id = ?
                """, event.status(), resolvedAt, event.updatedAt(), event.ticketId(), event.tenantId());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recruitmentJobChanged(RecruitmentJobProjectionChangedEvent event) {
        if (!inboxService.claim("analytics.recruitment-job")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_recruitment_job_projection
                    (job_id,tenant_id,status,created_at,updated_at,published_at,paused_at,closed_at,archived_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT (job_id) DO UPDATE SET status=EXCLUDED.status,updated_at=EXCLUDED.updated_at,
                    published_at=EXCLUDED.published_at,paused_at=EXCLUDED.paused_at,
                    closed_at=EXCLUDED.closed_at,archived_at=EXCLUDED.archived_at
                """, event.jobId(), event.tenantId(), event.status(), event.createdAt(), event.updatedAt(),
                event.publishedAt(), event.pausedAt(), event.closedAt(), event.archivedAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recruitmentApplicationChanged(RecruitmentApplicationProjectionChangedEvent event) {
        if (!inboxService.claim("analytics.recruitment-application")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_recruitment_application_projection
                    (application_id,tenant_id,job_id,status,created_at,updated_at,submitted_at,verified_at,withdrawn_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT (application_id) DO UPDATE SET status=EXCLUDED.status,updated_at=EXCLUDED.updated_at,
                    verified_at=EXCLUDED.verified_at,withdrawn_at=EXCLUDED.withdrawn_at
                """, event.applicationId(), event.tenantId(), event.jobId(), event.status(),
                event.createdAt(), event.updatedAt(), event.submittedAt(), event.verifiedAt(), event.withdrawnAt());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recruitmentErased(RecruitmentErasureCompletedEvent event) {
        jdbcTemplate.update("DELETE FROM analytics_recruitment_interview_projection WHERE tenant_id=? AND application_id=?",
                event.tenantId(),event.applicationId());
        jdbcTemplate.update("DELETE FROM analytics_recruitment_application_projection WHERE tenant_id=? AND application_id=?",
                event.tenantId(),event.applicationId());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recruitmentInterviewChanged(RecruitmentInterviewProjectionChangedEvent event) {
        if (!inboxService.claim("analytics.recruitment-interview")) return;
        jdbcTemplate.update("""
                INSERT INTO analytics_recruitment_interview_projection
                    (interview_id,tenant_id,application_id,job_id,status,created_at,updated_at,invited_at,
                     scheduled_start_at,scheduled_end_at,started_at,completed_at,cancelled_at,expired_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (interview_id) DO UPDATE SET status=EXCLUDED.status,updated_at=EXCLUDED.updated_at,
                    invited_at=EXCLUDED.invited_at,scheduled_start_at=EXCLUDED.scheduled_start_at,
                    scheduled_end_at=EXCLUDED.scheduled_end_at,started_at=EXCLUDED.started_at,
                    completed_at=EXCLUDED.completed_at,cancelled_at=EXCLUDED.cancelled_at,
                    expired_at=EXCLUDED.expired_at
                """, event.interviewId(), event.tenantId(), event.applicationId(), event.jobId(), event.status(),
                event.createdAt(), event.updatedAt(), event.invitedAt(), event.scheduledStartAt(),
                event.scheduledEndAt(), event.startedAt(), event.completedAt(), event.cancelledAt(), event.expiredAt());
    }
}
