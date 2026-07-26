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
    max_team_members,
    quota_anchor_at,
    api_access_enabled,
    webhooks_enabled,
    advanced_analytics_enabled,
    custom_branding_enabled,
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
    5,
    NOW(),
    TRUE,
    TRUE,
    TRUE,
    TRUE,
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
    max_team_members = EXCLUDED.max_team_members,
    quota_anchor_at = COALESCE(tenants.quota_anchor_at, EXCLUDED.quota_anchor_at),
    api_access_enabled = EXCLUDED.api_access_enabled,
    webhooks_enabled = EXCLUDED.webhooks_enabled,
    advanced_analytics_enabled = EXCLUDED.advanced_analytics_enabled,
    custom_branding_enabled = EXCLUDED.custom_branding_enabled,
    updated_at = NOW();

-- The local development tenant always has Recruitment and AI Interview access.
-- This deliberately overrides an OFF state whenever the development seed is rerun.
INSERT INTO recruitment_tenant_activation (
    tenant_id,
    rollout_stage,
    master_enabled,
    automation_enabled,
    cv_ai_enabled,
    calling_enabled,
    recording_enabled,
    public_discovery_enabled,
    created_at,
    updated_at
)
VALUES (
    (SELECT id FROM tenants WHERE slug = 'cacanode-demo'),
    'AUTO',
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    FALSE,
    NOW(),
    NOW()
)
ON CONFLICT (tenant_id) DO UPDATE
SET
    rollout_stage = EXCLUDED.rollout_stage,
    master_enabled = EXCLUDED.master_enabled,
    automation_enabled = EXCLUDED.automation_enabled,
    cv_ai_enabled = EXCLUDED.cv_ai_enabled,
    calling_enabled = EXCLUDED.calling_enabled,
    recording_enabled = EXCLUDED.recording_enabled,
    public_discovery_enabled = EXCLUDED.public_discovery_enabled,
    version = recruitment_tenant_activation.version + 1,
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

INSERT INTO model_config_versions (
    id,
    name,
    version_label,
    generation_model_id,
    generation_adapter_id,
    generation_runtime,
    generation_endpoint,
    text_embedding_model_id,
    text_embedding_dimension,
    text_embedding_runtime,
    image_embedding_model_id,
    audio_embedding_model_id,
    asr_model_id,
    ocr_model_id,
    status,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    'default-open-source-local',
    '2026-07-local',
    'google/gemma-4-it',
    'cacanode/gemma-4-vi-lora',
    'vLLM',
    'internal://model-gateway/generation',
    'google/embeddinggemma',
    768,
    'internal',
    'openai/clip-vit-base-patch32',
    'laion/clap-htsat-unfused',
    'openai/whisper-large-v3',
    'paddleocr/pp-ocrv5',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (name) DO UPDATE
SET
    version_label = EXCLUDED.version_label,
    generation_model_id = EXCLUDED.generation_model_id,
    generation_adapter_id = EXCLUDED.generation_adapter_id,
    generation_runtime = EXCLUDED.generation_runtime,
    generation_endpoint = EXCLUDED.generation_endpoint,
    text_embedding_model_id = EXCLUDED.text_embedding_model_id,
    text_embedding_dimension = EXCLUDED.text_embedding_dimension,
    text_embedding_runtime = EXCLUDED.text_embedding_runtime,
    image_embedding_model_id = EXCLUDED.image_embedding_model_id,
    audio_embedding_model_id = EXCLUDED.audio_embedding_model_id,
    asr_model_id = EXCLUDED.asr_model_id,
    ocr_model_id = EXCLUDED.ocr_model_id,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO knowledge_bases (
    id,
    tenant_id,
    name,
    slug,
    description,
    default_locale,
    status,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000004',
    (SELECT id FROM tenants WHERE slug = 'cacanode-demo'),
    'Default Knowledge Base',
    'default',
    'Default tenant-scoped knowledge base for local development.',
    'vi-VN',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (tenant_id, slug) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    default_locale = EXCLUDED.default_locale,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO chatbots (
    id,
    tenant_id,
    knowledge_base_id,
    model_config_version_id,
    display_name,
    default_locale,
    welcome_message,
    safe_instructions,
    response_tone,
    citation_policy,
    general_knowledge_policy,
    retrieval_settings,
    allowed_origins,
    status,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000005',
    (SELECT id FROM tenants WHERE slug = 'cacanode-demo'),
    (SELECT id FROM knowledge_bases WHERE tenant_id = (SELECT id FROM tenants WHERE slug = 'cacanode-demo') AND slug = 'default'),
    (SELECT id FROM model_config_versions WHERE name = 'default-open-source-local'),
    'CacaNode Assistant',
    'vi-VN',
    'Xin chao! Toi co the giup gi cho ban?',
    'Answer from the tenant knowledge base when possible. Be clear when information is unavailable, avoid fabricating tenant-specific facts, and include citations when using retrieved sources.',
    'HELPFUL',
    'REQUIRED_FOR_KNOWLEDGE',
    'ALLOW_WITH_DISCLOSURE',
    '{"topK": 8, "graphDepth": 2, "rerank": true, "minScore": 0.35}'::jsonb,
    '["http://localhost:3000", "http://127.0.0.1:3000"]'::jsonb,
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (id) DO UPDATE
SET
    tenant_id = EXCLUDED.tenant_id,
    knowledge_base_id = EXCLUDED.knowledge_base_id,
    model_config_version_id = EXCLUDED.model_config_version_id,
    display_name = EXCLUDED.display_name,
    default_locale = EXCLUDED.default_locale,
    welcome_message = EXCLUDED.welcome_message,
    safe_instructions = EXCLUDED.safe_instructions,
    response_tone = EXCLUDED.response_tone,
    citation_policy = EXCLUDED.citation_policy,
    general_knowledge_policy = EXCLUDED.general_knowledge_policy,
    retrieval_settings = EXCLUDED.retrieval_settings,
    allowed_origins = EXCLUDED.allowed_origins,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO widget_configs (
    id,
    tenant_id,
    chatbot_id,
    display_name,
    welcome_message,
    primary_color,
    position,
    is_active,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000006',
    (SELECT id FROM tenants WHERE slug = 'cacanode-demo'),
    (SELECT id FROM chatbots WHERE id = '00000000-0000-0000-0000-000000000005'),
    'CacaNode Assistant',
    'Xin chao! Toi co the giup gi cho ban?',
    '#4f46e5',
    'BOTTOM_RIGHT',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (chatbot_id) DO UPDATE
SET
    tenant_id = EXCLUDED.tenant_id,
    display_name = EXCLUDED.display_name,
    welcome_message = EXCLUDED.welcome_message,
    primary_color = EXCLUDED.primary_color,
    position = EXCLUDED.position,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

INSERT INTO billing_subscriptions (
    tenant_id,
    plan_code,
    status,
    billing_interval,
    catalog_version,
    quota_anchor_at,
    paid_through_at,
    grace_ends_at,
    entitlement_snapshot,
    created_at,
    updated_at
)
VALUES (
    (SELECT id FROM tenants WHERE slug = 'cacanode-demo'),
    'PRO',
    'ACTIVE',
    'MONTHLY',
    '2026-07-23',
    NOW(),
    NOW() + INTERVAL '1 year',
    NOW() + INTERVAL '1 year 3 days',
    jsonb_build_object(
        'maxMessages', 5000,
        'maxDocuments', 150,
        'maxTeamMembers', 5,
        'maxStorageMb', 5120,
        'maxActiveJobs', 3,
        'maxVerifiedApplications', 150,
        'maxInterviewSeconds', 3600,
        'maxCvAnalyses', 100,
        'maxRecruitmentStorageBytes', 1073741824,
        'apiAccess', TRUE,
        'webhooks', TRUE,
        'advancedAnalytics', TRUE,
        'customBranding', TRUE
    ),
    NOW(),
    NOW()
)
ON CONFLICT (tenant_id) DO UPDATE
SET
    plan_code = EXCLUDED.plan_code,
    status = EXCLUDED.status,
    billing_interval = EXCLUDED.billing_interval,
    catalog_version = EXCLUDED.catalog_version,
    paid_through_at = EXCLUDED.paid_through_at,
    grace_ends_at = EXCLUDED.grace_ends_at,
    cancel_at_period_end = FALSE,
    entitlement_snapshot = EXCLUDED.entitlement_snapshot,
    updated_at = NOW();
