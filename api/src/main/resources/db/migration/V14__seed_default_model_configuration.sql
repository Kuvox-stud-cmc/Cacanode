-- Tenant workspace provisioning requires at least one active platform model
-- configuration. Preserve an existing active configuration; otherwise seed a
-- default that matches the production generation and embedding runtimes.
INSERT INTO model_config_versions (
    name,
    version_label,
    generation_model_id,
    generation_runtime,
    generation_endpoint,
    text_embedding_model_id,
    text_embedding_dimension,
    text_embedding_runtime,
    status
)
SELECT
    'platform-default',
    '2026-07',
    'o4-mini',
    'openai',
    'https://api.openai.com/v1',
    'embeddinggemma',
    768,
    'ollama',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM model_config_versions
    WHERE status = 'ACTIVE'
)
ON CONFLICT (name) DO UPDATE SET
    version_label = EXCLUDED.version_label,
    generation_model_id = EXCLUDED.generation_model_id,
    generation_runtime = EXCLUDED.generation_runtime,
    generation_endpoint = EXCLUDED.generation_endpoint,
    text_embedding_model_id = EXCLUDED.text_embedding_model_id,
    text_embedding_dimension = EXCLUDED.text_embedding_dimension,
    text_embedding_runtime = EXCLUDED.text_embedding_runtime,
    status = 'ACTIVE',
    updated_at = NOW();
