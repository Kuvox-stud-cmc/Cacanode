DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tenants)
    AND NOT EXISTS (
        SELECT 1
        FROM model_config_versions
        WHERE status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'Cannot backfill default tenant workspaces: no active model configuration exists';
    END IF;
END $$;

INSERT INTO knowledge_bases (
    tenant_id,
    name,
    slug,
    description,
    default_locale,
    status,
    created_at,
    updated_at
)
SELECT
    t.id,
    'Default Knowledge Base',
    'default',
    'Default tenant-scoped knowledge base.',
    'vi-VN',
    'ACTIVE',
    NOW(),
    NOW()
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1
    FROM knowledge_bases kb
    WHERE kb.tenant_id = t.id
      AND kb.slug = 'default'
);

INSERT INTO chatbots (
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
SELECT
    kb.tenant_id,
    kb.id,
    model_config.id,
    'CacaNode Assistant',
    kb.default_locale,
    'Xin chao! Toi co the giup gi cho ban?',
    'Answer from the tenant knowledge base when possible. Be clear when information is unavailable, avoid fabricating tenant-specific facts, and include citations when using retrieved sources.',
    'HELPFUL',
    'REQUIRED_FOR_KNOWLEDGE',
    'ALLOW_WITH_DISCLOSURE',
    '{"topK": 8, "graphDepth": 2, "rerank": true, "minScore": 0.35}'::jsonb,
    '[]'::jsonb,
    'ACTIVE',
    NOW(),
    NOW()
FROM knowledge_bases kb
CROSS JOIN LATERAL (
    SELECT id
    FROM model_config_versions
    WHERE status = 'ACTIVE'
    ORDER BY created_at DESC
    LIMIT 1
) model_config
WHERE kb.slug = 'default'
  AND NOT EXISTS (
      SELECT 1
      FROM chatbots c
      WHERE c.tenant_id = kb.tenant_id
        AND c.knowledge_base_id = kb.id
        AND c.status = 'ACTIVE'
  );

INSERT INTO widget_configs (
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
SELECT
    c.tenant_id,
    c.id,
    c.display_name,
    c.welcome_message,
    '#4f46e5',
    'BOTTOM_RIGHT',
    TRUE,
    NOW(),
    NOW()
FROM chatbots c
WHERE c.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM widget_configs wc
      WHERE wc.chatbot_id = c.id
  );
