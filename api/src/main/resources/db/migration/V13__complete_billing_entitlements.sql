ALTER TABLE usage_metrics
    ADD COLUMN warning_80_sent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN exceeded_sent BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE widget_configs
    ADD COLUMN hide_cacanode_branding BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'WELCOME_EMAIL', 'LOGIN_2FA_EMAIL', 'DOCUMENT_COMPLETED', 'DOCUMENT_FAILED', 'USER_INVITED',
    'QUOTA_WARNING', 'QUOTA_EXCEEDED', 'BILLING_RENEWAL', 'BILLING_GRACE'
));

INSERT INTO billing_subscriptions (
    tenant_id, plan_code, status, billing_interval, catalog_version, quota_anchor_at,
    trial_ends_at, paid_through_at, grace_ends_at, entitlement_snapshot
)
SELECT id,
       CASE WHEN plan = 'TRIAL' THEN 'TRIAL' WHEN plan = 'PRO' THEN 'PRO'
            WHEN plan = 'ENTERPRISE' THEN 'ENTERPRISE' ELSE 'STARTER' END,
       CASE WHEN plan = 'TRIAL' THEN 'TRIAL' WHEN plan = 'PRO' THEN 'ACTIVE'
            WHEN plan = 'ENTERPRISE' THEN 'ENTERPRISE' ELSE 'STARTER' END,
       CASE WHEN plan = 'PRO' THEN 'MONTHLY' ELSE NULL END,
       '2026-07-15',
       COALESCE(quota_anchor_at, created_at),
       CASE WHEN plan = 'TRIAL' THEN trial_ends_at ELSE NULL END,
       CASE WHEN plan = 'PRO' THEN NOW() + INTERVAL '1 month' ELSE NULL END,
       CASE WHEN plan = 'PRO' THEN NOW() + INTERVAL '1 month 3 days' ELSE NULL END,
       jsonb_build_object(
           'maxMessages', max_messages, 'maxDocuments', max_documents,
           'maxTeamMembers', max_team_members, 'maxStorageMb', max_storage_mb,
           'apiAccess', api_access_enabled, 'webhooks', webhooks_enabled,
           'advancedAnalytics', advanced_analytics_enabled, 'customBranding', custom_branding_enabled
       )
FROM tenants
ON CONFLICT (tenant_id) DO NOTHING;

UPDATE tenants t
SET paid_through_at = s.paid_through_at,
    grace_ends_at = s.grace_ends_at
FROM billing_subscriptions s
WHERE s.tenant_id = t.id AND s.plan_code = 'PRO';
