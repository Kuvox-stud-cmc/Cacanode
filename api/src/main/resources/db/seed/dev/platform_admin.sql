-- Local development platform administrator seed.
-- Login: platform@cacanode.local / Cacanode@123

INSERT INTO tenants (
    id,
    name,
    slug,
    kind,
    plan,
    status,
    max_documents,
    max_messages,
    max_storage_mb,
    max_team_members,
    api_access_enabled,
    webhooks_enabled,
    advanced_analytics_enabled,
    custom_branding_enabled,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-ffffffffffff',
    'CacaNode Platform',
    'cacanode-platform',
    'PLATFORM_INTERNAL',
    'ENTERPRISE',
    'ACTIVE',
    0,
    0,
    0,
    0,
    FALSE,
    FALSE,
    FALSE,
    FALSE,
    NOW(),
    NOW()
)
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    kind = EXCLUDED.kind,
    plan = EXCLUDED.plan,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO users (
    id,
    tenant_id,
    email,
    password_hash,
    full_name,
    role,
    status,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-fffffffffffe',
    (SELECT id FROM tenants WHERE slug = 'cacanode-platform'),
    'bao15022016@gmail.com',
    '$2a$10$Laysy8kywXRV.SUY.S6f7OYcTRQHA5ZRlepUVO1LH75W6KD06e33a',
    'Platform Admin',
    'PLATFORM_ADMIN',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (email) DO UPDATE
SET
    tenant_id = EXCLUDED.tenant_id,
    password_hash = EXCLUDED.password_hash,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    updated_at = NOW();
