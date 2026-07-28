package com.cacanode.api.recruitment.query;

import com.cacanode.api.common.api.operations.OperationalFailureReadApi;
import com.cacanode.api.common.config.OperationalFailureProperties;
import com.cacanode.api.common.service.OperationalFailureQueryExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitmentOperationalFailureReader implements OperationalFailureReadApi {
    private static final String CV_SQL = """
            SELECT md5(a.id::text || ':cv-analysis-failure')::uuid failure_id,a.tenant_id,app.job_id resource_id,'JOB' resource_type,
              CASE WHEN a.status='FAILED' THEN 'FAILED' WHEN a.publish_attempts>=10 THEN 'DEAD' ELSE 'RETRYING' END state,
              CASE WHEN a.status='FAILED' THEN 'ERROR' WHEN a.publish_attempts>=10 THEN 'CRITICAL' ELSE 'WARNING' END severity,
              CASE WHEN a.failure_code='CV_ANALYSIS_PUBLICATION_FAILED' OR a.status='QUEUED'
                THEN 'CV_ANALYSIS_PUBLICATION_FAILED' ELSE 'CV_ANALYSIS_FAILED' END error_code,
              a.publish_attempts attempts,a.created_at first_seen_at,a.updated_at last_seen_at,
              CASE WHEN a.status='QUEUED' AND a.publish_attempts<10 THEN a.next_publish_at ELSE NULL END next_retry_at
            FROM recruitment_cv_analyses a JOIN recruitment_applications app
              ON app.tenant_id=a.tenant_id AND app.id=a.application_id
            WHERE a.status='FAILED' OR (a.status='QUEUED' AND a.publish_attempts>0)
            """;
    private static final String TRANSPORT_SQL = """
            SELECT md5(c.id::text || ':interview-transport-failure')::uuid failure_id,c.tenant_id,c.job_id resource_id,'JOB' resource_type,
              CASE WHEN c.create_outcome_uncertain THEN 'STALLED' ELSE 'FAILED' END state,
              CASE WHEN c.create_outcome_uncertain THEN 'WARNING' ELSE 'ERROR' END severity,
              CASE WHEN c.create_outcome_uncertain THEN 'INTERVIEW_TRANSPORT_STALLED' ELSE 'INTERVIEW_TRANSPORT_FAILED' END error_code,
              GREATEST(c.attempt_number,c.preparation_attempts) attempts,c.created_at first_seen_at,c.updated_at last_seen_at,
              CASE WHEN c.create_outcome_uncertain THEN c.create_uncertain_until ELSE c.next_retry_at END next_retry_at
            FROM recruitment_interview_call_attempts c
            WHERE (c.status='FAILED' OR c.create_outcome_uncertain=TRUE)
              AND COALESCE(c.failure_code,'') NOT IN ('CONSENT_DECLINED','CONSENT_NOT_RECEIVED','INTERVIEW_CANCELLED',
                'INTERVIEW_EXPIRED','TWILIO_CANCELLED','DIAL_WINDOW_EXPIRED','APPLICATION_NOT_SCHEDULED',
                'INTERVIEW_NOT_SCHEDULED','JOB_NOT_CALLABLE','QUOTA_RESERVATION_INVALID',
                'INTERVIEW_QUOTA_EXHAUSTED','TENANT_CALLING_KILL_SWITCH','FRAUD_REJECTED','ELIGIBILITY_REJECTED',
                'FEATURE_DISABLED','NO_ANSWER','DECLINED','CANCELLED','EXPIRED')
            UNION ALL
            SELECT md5(r.session_id::text || ':interview-result-failure')::uuid,c.tenant_id,c.job_id,'JOB',
              CASE WHEN r.terminal_kind='FAILED' THEN 'FAILED' ELSE 'STALLED' END,
              'ERROR',CASE WHEN r.terminal_kind='FAILED' THEN 'INTERVIEW_RESULT_FAILED' ELSE 'INTERVIEW_RESULT_STALLED' END,
              1,r.created_at,r.updated_at,NULL::timestamp
            FROM recruitment_interview_results r JOIN recruitment_interview_call_attempts c
              ON c.tenant_id=r.tenant_id AND c.id=r.call_attempt_id
            WHERE (r.terminal_kind='FAILED' AND COALESCE(r.failure_code,'') NOT IN
                ('CANDIDATE_REJECTED','NO_ANSWER','DECLINED','CANCELLED','EXPIRED','FRAUD_REJECTED','ELIGIBILITY_REJECTED','FEATURE_DISABLED'))
               OR (r.delivery_status='PENDING_TURNS' AND r.updated_at<?)
            """;
    private static final String EMAIL_SQL = """
            SELECT md5(e.id::text || ':candidate-email-failure')::uuid failure_id,e.tenant_id,i.job_id resource_id,'JOB' resource_type,
              CASE WHEN e.attempts>=10 THEN 'DEAD' ELSE 'RETRYING' END state,
              CASE WHEN e.attempts>=10 THEN 'ERROR' ELSE 'WARNING' END severity,
              CASE WHEN e.attempts>=10 THEN 'CANDIDATE_EMAIL_EXHAUSTED' ELSE 'CANDIDATE_EMAIL_RETRY' END error_code,
              e.attempts,e.created_at first_seen_at,e.updated_at last_seen_at,
              CASE WHEN e.attempts>=10 THEN NULL ELSE e.next_attempt_at END next_retry_at
            FROM recruitment_candidate_email_deliveries e JOIN recruitment_interviews i
              ON i.tenant_id=e.tenant_id AND i.id=e.interview_id WHERE e.state='FAILED'
            """;
    private static final String RECORDING_SQL = """
            SELECT md5(o.id::text || ':recording-operation-failure')::uuid failure_id,o.tenant_id,c.job_id resource_id,'JOB' resource_type,
              CASE WHEN o.status='DEAD' THEN 'DEAD' WHEN o.status='PROCESSING' AND o.locked_at<? THEN 'STALLED' ELSE 'RETRYING' END state,
              CASE WHEN o.status='DEAD' THEN 'CRITICAL' WHEN o.status='PROCESSING' AND o.locked_at<? THEN 'ERROR' ELSE 'WARNING' END severity,
              CASE WHEN o.status='DEAD' THEN 'RECORDING_OPERATION_DEAD' ELSE 'RECORDING_OPERATION_RETRY' END error_code,
              o.attempts,o.created_at first_seen_at,o.updated_at last_seen_at,
              CASE WHEN o.status='DEAD' THEN NULL ELSE o.next_attempt_at END next_retry_at
            FROM recruitment_recording_operations o JOIN recruitment_interview_recordings r
              ON r.tenant_id=o.tenant_id AND r.id=o.recording_id
              JOIN recruitment_interview_call_attempts c ON c.tenant_id=r.tenant_id AND c.id=r.call_attempt_id
            WHERE o.status IN ('PENDING','PROCESSING','DEAD')
              AND (o.attempts>0 OR o.status='DEAD' OR (o.status='PROCESSING' AND o.locked_at<?))
            UNION ALL
            SELECT md5(o.id::text || ':notification')::uuid,o.tenant_id,c.job_id,'JOB',
              CASE WHEN o.notification_attempts>=10 THEN 'DEAD' ELSE 'RETRYING' END,
              CASE WHEN o.notification_attempts>=10 THEN 'CRITICAL' ELSE 'WARNING' END,
              CASE WHEN o.notification_attempts>=10 THEN 'RECORDING_NOTIFICATION_DEAD' ELSE 'RECORDING_NOTIFICATION_RETRY' END,
              o.notification_attempts,o.created_at,o.updated_at,
              CASE WHEN o.notification_attempts>=10 THEN NULL ELSE o.notification_next_attempt_at END
            FROM recruitment_recording_operations o JOIN recruitment_interview_recordings r
              ON r.tenant_id=o.tenant_id AND r.id=o.recording_id
              JOIN recruitment_interview_call_attempts c ON c.tenant_id=r.tenant_id AND c.id=r.call_attempt_id
            WHERE o.notification_published_at IS NULL AND o.notification_attempts>0
            UNION ALL
            SELECT md5(r.id::text || ':recording-failure')::uuid,r.tenant_id,c.job_id,'JOB','FAILED','ERROR','RECORDING_FAILED',1,
              r.created_at,r.updated_at,NULL::timestamp
            FROM recruitment_interview_recordings r JOIN recruitment_interview_call_attempts c
              ON c.tenant_id=r.tenant_id AND c.id=r.call_attempt_id WHERE r.state='FAILED'
            """;
    private static final String PRIVACY_SQL = """
            SELECT md5(p.id::text || ':privacy-erasure-failure')::uuid failure_id,p.tenant_id,a.job_id resource_id,'JOB' resource_type,
              CASE WHEN p.status='EXHAUSTED' THEN 'DEAD' ELSE 'RETRYING' END state,
              CASE WHEN p.status='EXHAUSTED' THEN 'CRITICAL' ELSE 'WARNING' END severity,
              CASE WHEN p.status='EXHAUSTED' THEN 'PRIVACY_ERASURE_EXHAUSTED' ELSE 'PRIVACY_ERASURE_RETRY' END error_code,
              p.attempts,p.created_at first_seen_at,p.updated_at last_seen_at,
              CASE WHEN p.status='EXHAUSTED' THEN NULL ELSE p.next_attempt_at END next_retry_at
            FROM recruitment_privacy_deletion_requests p JOIN recruitment_applications a
              ON a.tenant_id=p.tenant_id AND a.id=p.application_id WHERE p.status IN ('RETRY','EXHAUSTED')
            """;

    private final OperationalFailureQueryExecutor queries;
    private final OperationalFailureProperties properties;
    private final Clock clock;

    public Set<Source> sources() { return Set.of(Source.CV_ANALYSIS, Source.INTERVIEW_TRANSPORT,
            Source.CANDIDATE_EMAIL, Source.RECORDING, Source.PRIVACY_ERASURE); }
    public Summary summary(Source source, Optional<UUID> tenantId) {
        return queries.summary(sql(source), args(source), source, tenantId);
    }
    public Page failures(Source source, Query query) { return queries.page(sql(source), args(source), source, query); }
    public List<Failure> recent(Source source, Optional<UUID> tenantId, int limit) {
        return queries.recent(sql(source), args(source), source, tenantId, limit);
    }
    private String sql(Source source) {
        return switch (source) {
            case CV_ANALYSIS -> CV_SQL;
            case INTERVIEW_TRANSPORT -> TRANSPORT_SQL;
            case CANDIDATE_EMAIL -> EMAIL_SQL;
            case RECORDING -> RECORDING_SQL;
            case PRIVACY_ERASURE -> PRIVACY_SQL;
            default -> throw new IllegalArgumentException("Unsupported recruitment failure source");
        };
    }
    private List<?> args(Source source) {
        LocalDateTime threshold = LocalDateTime.now(clock).minus(properties.failureStalledAfter());
        return switch (source) {
            case INTERVIEW_TRANSPORT -> List.of(threshold);
            case RECORDING -> List.of(threshold, threshold, threshold);
            default -> List.of();
        };
    }
}
