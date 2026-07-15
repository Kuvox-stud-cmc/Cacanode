CREATE SEQUENCE billing_order_code_seq START WITH 100000 INCREMENT BY 1;

ALTER TABLE tenants ADD COLUMN max_team_members INTEGER;
ALTER TABLE tenants ADD COLUMN quota_anchor_at TIMESTAMP;
ALTER TABLE tenants ADD COLUMN paid_through_at TIMESTAMP;
ALTER TABLE tenants ADD COLUMN grace_ends_at TIMESTAMP;
ALTER TABLE tenants ADD COLUMN api_access_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenants ADD COLUMN webhooks_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenants ADD COLUMN advanced_analytics_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenants ADD COLUMN custom_branding_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenants ALTER COLUMN max_documents DROP NOT NULL;
ALTER TABLE tenants ALTER COLUMN max_messages DROP NOT NULL;
ALTER TABLE tenants ALTER COLUMN max_storage_mb DROP NOT NULL;

UPDATE tenants
SET max_team_members = CASE WHEN plan = 'ENTERPRISE' THEN NULL WHEN plan IN ('PRO', 'TRIAL') THEN 5 ELSE 1 END,
    quota_anchor_at = COALESCE(created_at, NOW()),
    max_documents = CASE WHEN plan = 'ENTERPRISE' THEN NULL WHEN plan IN ('PRO', 'TRIAL') THEN 50 ELSE 3 END,
    max_messages = CASE WHEN plan = 'ENTERPRISE' THEN NULL WHEN plan IN ('PRO', 'TRIAL') THEN 10000 ELSE 500 END,
    max_storage_mb = CASE WHEN plan = 'ENTERPRISE' THEN NULL WHEN plan IN ('PRO', 'TRIAL') THEN 10240 ELSE 512 END,
    api_access_enabled = plan IN ('PRO', 'TRIAL', 'ENTERPRISE'),
    webhooks_enabled = plan IN ('PRO', 'TRIAL', 'ENTERPRISE'),
    advanced_analytics_enabled = plan IN ('PRO', 'TRIAL', 'ENTERPRISE'),
    custom_branding_enabled = plan IN ('PRO', 'TRIAL', 'ENTERPRISE');

ALTER TABLE usage_metrics ADD COLUMN period_start TIMESTAMP;
ALTER TABLE usage_metrics ADD COLUMN period_end TIMESTAMP;
UPDATE usage_metrics
SET period_start = make_timestamp(period_year, period_month, 1, 0, 0, 0),
    period_end = make_timestamp(period_year, period_month, 1, 0, 0, 0) + INTERVAL '1 month';
ALTER TABLE usage_metrics ALTER COLUMN period_start SET NOT NULL;
ALTER TABLE usage_metrics ALTER COLUMN period_end SET NOT NULL;
ALTER TABLE usage_metrics DROP CONSTRAINT uq_usage_metric_tenant_period;
ALTER TABLE usage_metrics ADD CONSTRAINT uq_usage_metric_tenant_period_start UNIQUE (tenant_id, period_start);

CREATE TABLE billing_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    plan_code VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    billing_interval VARCHAR(20),
    catalog_version VARCHAR(50) NOT NULL,
    quota_anchor_at TIMESTAMP NOT NULL,
    trial_ends_at TIMESTAMP,
    paid_through_at TIMESTAMP,
    grace_ends_at TIMESTAMP,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_7_sent_at TIMESTAMP,
    reminder_3_sent_at TIMESTAMP,
    reminder_1_sent_at TIMESTAMP,
    last_grace_reminder_at TIMESTAMP,
    entitlement_snapshot JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_billing_subscription_status ON billing_subscriptions(status);

CREATE TABLE billing_payment_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    order_code BIGINT NOT NULL UNIQUE DEFAULT nextval('billing_order_code_seq'),
    requested_plan VARCHAR(30) NOT NULL,
    billing_interval VARCHAR(20) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    catalog_version VARCHAR(50) NOT NULL,
    entitlement_snapshot JSONB NOT NULL,
    payment_link_id VARCHAR(255),
    checkout_url TEXT,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_reference VARCHAR(255),
    paid_at TIMESTAMP,
    failure_reason TEXT,
    client_idempotency_key VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_billing_payment_tenant_idempotency UNIQUE (tenant_id, client_idempotency_key)
);
CREATE INDEX idx_billing_payment_tenant_status ON billing_payment_orders(tenant_id, status);
CREATE INDEX idx_billing_payment_reconciliation ON billing_payment_orders(status, expires_at);

CREATE TABLE billing_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_order_id UUID REFERENCES billing_payment_orders(id) ON DELETE SET NULL,
    provider_reference VARCHAR(255),
    payload_hash VARCHAR(64) NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    processing_result VARCHAR(50) NOT NULL,
    failure_reason TEXT,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_billing_webhook_payload_hash UNIQUE (payload_hash)
);
CREATE INDEX idx_billing_webhook_payment_order ON billing_webhook_events(payment_order_id);
