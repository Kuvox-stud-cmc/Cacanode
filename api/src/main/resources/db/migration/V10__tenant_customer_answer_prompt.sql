ALTER TABLE tenants
    ADD COLUMN customer_answer_prompt TEXT;

UPDATE tenants t
SET customer_answer_prompt = COALESCE(
    (
        SELECT LEFT(REGEXP_REPLACE(c.safe_instructions, '^\s+|\s+$', '', 'g'), 4000)
        FROM chatbots c
        WHERE c.tenant_id = t.id
          AND c.status = 'ACTIVE'
          AND CHAR_LENGTH(REGEXP_REPLACE(c.safe_instructions, '^\s+|\s+$', '', 'g')) > 0
        ORDER BY c.created_at ASC, c.id ASC
        LIMIT 1
    ),
    'Answer from the tenant knowledge base when possible. Be clear when information is unavailable, avoid fabricating tenant-specific facts, and include citations when using retrieved sources.'
);

ALTER TABLE tenants
    ALTER COLUMN customer_answer_prompt SET DEFAULT
        'Answer from the tenant knowledge base when possible. Be clear when information is unavailable, avoid fabricating tenant-specific facts, and include citations when using retrieved sources.',
    ALTER COLUMN customer_answer_prompt SET NOT NULL,
    ADD CONSTRAINT tenants_customer_answer_prompt_length_check
        CHECK (
            CHAR_LENGTH(REGEXP_REPLACE(customer_answer_prompt, '^\s+|\s+$', '', 'g'))
                BETWEEN 1 AND 4000
        );
