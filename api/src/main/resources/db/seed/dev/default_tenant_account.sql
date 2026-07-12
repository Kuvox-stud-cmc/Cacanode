-- Local development seed data.
-- Login: admin@cacanode.local / Cacanode@123

INSERT INTO tenants (
    id,
    name,
    slug,
    plan,
    status,
    max_documents,
    max_messages,
    max_storage_mb,
    llm_provider,
    llm_model,
    embed_provider,
    embed_model,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CacaNode Demo',
    'cacanode-demo',
    'PRO',
    'ACTIVE',
    150,
    5000,
    5120,
    'groq',
    'llama-3.3-70b-versatile',
    'voyageai',
    'voyage-3',
    NOW(),
    NOW()
)
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    plan = EXCLUDED.plan,
    status = EXCLUDED.status,
    max_documents = EXCLUDED.max_documents,
    max_messages = EXCLUDED.max_messages,
    max_storage_mb = EXCLUDED.max_storage_mb,
    llm_provider = EXCLUDED.llm_provider,
    llm_model = EXCLUDED.llm_model,
    embed_provider = EXCLUDED.embed_provider,
    embed_model = EXCLUDED.embed_model,
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
    '00000000-0000-0000-0000-000000000002',
    (SELECT id FROM tenants WHERE slug = 'cacanode-demo'),
    'admin@cacanode.local',
    '$2a$10$Laysy8kywXRV.SUY.S6f7OYcTRQHA5ZRlepUVO1LH75W6KD06e33a',
    'CacaNode Admin',
    'TENANT_ADMIN',
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

INSERT INTO widget_configs (
    id,
    tenant_id,
    display_name,
    welcome_message,
    primary_color,
    position,
    is_active,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    (SELECT id FROM tenants WHERE slug = 'cacanode-demo'),
    'CacaNode Assistant',
    'Hi! How can I help you today?',
    '#4f46e5',
    'BOTTOM_RIGHT',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (tenant_id) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    welcome_message = EXCLUDED.welcome_message,
    primary_color = EXCLUDED.primary_color,
    position = EXCLUDED.position,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();
