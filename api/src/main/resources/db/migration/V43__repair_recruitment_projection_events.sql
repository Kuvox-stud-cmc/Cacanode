-- Repair projection events poisoned by the initial recruitment analytics implementation.
-- New-job events could be serialized before Hibernate assigned audit timestamps, while
-- scheduled interview events failed because java.time.Instant was bound directly through JDBC.

UPDATE module_event_outbox AS event
SET payload = jsonb_set(
        jsonb_set(event.payload, '{createdAt}', to_jsonb(job.created_at), TRUE),
        '{updatedAt}', to_jsonb(job.updated_at), TRUE)
FROM recruitment_jobs AS job
WHERE event.status = 'DEAD'
  AND event.event_type = 'recruitment.job.projection.v1'
  AND event.last_error LIKE '%analytics_recruitment_job_projection%'
  AND event.payload ->> 'tenantId' = job.tenant_id::TEXT
  AND event.payload ->> 'jobId' = job.id::TEXT
  AND (
      event.payload -> 'createdAt' IS NULL
      OR event.payload -> 'createdAt' = 'null'::JSONB
      OR event.payload -> 'updatedAt' IS NULL
      OR event.payload -> 'updatedAt' = 'null'::JSONB
  );

UPDATE module_event_outbox
SET status = 'PENDING',
    attempts = 0,
    published_at = NULL,
    next_attempt_at = CURRENT_TIMESTAMP,
    last_error = NULL
WHERE status = 'DEAD'
  AND (
      (
          event_type = 'recruitment.job.projection.v1'
          AND payload -> 'createdAt' IS NOT NULL
          AND payload -> 'createdAt' <> 'null'::JSONB
          AND payload -> 'updatedAt' IS NOT NULL
          AND payload -> 'updatedAt' <> 'null'::JSONB
          AND last_error LIKE '%analytics_recruitment_job_projection%'
      )
      OR
      (
          event_type = 'recruitment.interview.projection.v1'
          AND last_error LIKE '%analytics_recruitment_interview_projection%'
          AND (
              (payload -> 'scheduledStartAt' IS NOT NULL
                  AND payload -> 'scheduledStartAt' <> 'null'::JSONB)
              OR
              (payload -> 'scheduledEndAt' IS NOT NULL
                  AND payload -> 'scheduledEndAt' <> 'null'::JSONB)
          )
      )
  );
