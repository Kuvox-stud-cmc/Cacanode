CREATE INDEX idx_analytics_tenant_customer_status_plan_created
    ON analytics_tenant_projection (tenant_kind, status, plan, created_at, tenant_id);
CREATE INDEX idx_analytics_tenant_customer_updated
    ON analytics_tenant_projection (tenant_kind, updated_at, tenant_id);
CREATE INDEX idx_analytics_recruitment_job_published_global
    ON analytics_recruitment_job_projection (published_at, tenant_id) WHERE published_at IS NOT NULL;
CREATE INDEX idx_analytics_recruitment_application_verified_global
    ON analytics_recruitment_application_projection (verified_at, tenant_id) WHERE verified_at IS NOT NULL;
CREATE INDEX idx_analytics_recruitment_interview_status_updated_global
    ON analytics_recruitment_interview_projection (status, updated_at, tenant_id);
CREATE INDEX idx_module_event_outbox_status_updated
    ON module_event_outbox (status, next_attempt_at, created_at, event_id);
CREATE INDEX idx_document_failure_tenant_updated
    ON documents (tenant_id, status, updated_at, id);
CREATE INDEX idx_internal_event_outbox_status_updated
    ON internal_event_outbox (status, next_attempt_at, updated_at, id);
CREATE INDEX idx_webhook_outbox_tenant_status_updated
    ON webhook_outbox (tenant_id, status, next_attempt_at, created_at, id);
CREATE INDEX idx_billing_payment_tenant_status_updated
    ON billing_payment_orders (tenant_id, status, updated_at, id);
CREATE INDEX idx_billing_webhook_result_updated
    ON billing_webhook_events (processing_result, received_at, id);
CREATE INDEX idx_recruitment_cv_failure_tenant_updated
    ON recruitment_cv_analyses (tenant_id, status, updated_at, id);
CREATE INDEX idx_recruitment_call_failure_tenant_updated
    ON recruitment_interview_call_attempts (tenant_id, status, updated_at, id);
CREATE INDEX idx_recruitment_email_failure_tenant_updated
    ON recruitment_candidate_email_deliveries (tenant_id, state, updated_at, id);
CREATE INDEX idx_recruitment_recording_failure_tenant_updated
    ON recruitment_interview_recordings (tenant_id, state, updated_at, id);
CREATE INDEX idx_recruitment_recording_operation_failure_tenant_updated
    ON recruitment_recording_operations (tenant_id, status, updated_at, id);
CREATE INDEX idx_recruitment_privacy_failure_tenant_updated
    ON recruitment_privacy_deletion_requests (tenant_id, status, updated_at, id);
