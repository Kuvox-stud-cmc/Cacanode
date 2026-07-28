ALTER TABLE tenants
    ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE tenants
    ADD CONSTRAINT chk_tenants_kind
        CHECK (kind IN ('CUSTOMER', 'PLATFORM_INTERNAL'));

CREATE UNIQUE INDEX uq_tenants_single_platform_internal
    ON tenants (kind)
    WHERE kind = 'PLATFORM_INTERNAL';

ALTER TABLE analytics_tenant_projection
    ADD COLUMN tenant_kind VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE analytics_tenant_projection
    ADD CONSTRAINT chk_analytics_tenant_projection_kind
        CHECK (tenant_kind IN ('CUSTOMER', 'PLATFORM_INTERNAL'));

CREATE INDEX idx_analytics_tenant_projection_kind
    ON analytics_tenant_projection (tenant_kind, tenant_id);
