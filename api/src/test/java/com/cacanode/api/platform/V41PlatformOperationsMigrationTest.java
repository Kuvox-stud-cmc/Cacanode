package com.cacanode.api.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class V41PlatformOperationsMigrationTest {
    @Test
    void migrationIndexesCustomerAnalyticsAndEveryFailureOwner() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V41__platform_overview_and_failure_indexes.sql"));
        assertThat(sql).contains("analytics_tenant_projection", "analytics_recruitment_job_projection",
                "analytics_recruitment_application_projection", "analytics_recruitment_interview_projection",
                "module_event_outbox", "documents", "internal_event_outbox", "webhook_outbox",
                "billing_payment_orders", "billing_webhook_events", "recruitment_cv_analyses",
                "recruitment_interview_call_attempts", "recruitment_candidate_email_deliveries",
                "recruitment_interview_recordings", "recruitment_recording_operations",
                "recruitment_privacy_deletion_requests");
    }
}
