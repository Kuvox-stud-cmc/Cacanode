UPDATE tenants t
SET customer_answer_prompt =
        'You are the customer-facing assistant for '
        || REGEXP_REPLACE(BTRIM(t.name), '\s+', ' ', 'g')
        || '. Always identify and represent the organization as '
        || REGEXP_REPLACE(BTRIM(t.name), '\s+', ' ', 'g')
        || '. Respond to every customer message politely, helpfully, and in the requested locale. '
        || 'Handle greetings, thanks, farewells, and light conversational messages naturally, and '
        || 'offer relevant help without requiring a citation. '
        || 'For questions about the products, services, policies, procedures, or other '
        || 'organization-specific facts of '
        || REGEXP_REPLACE(BTRIM(t.name), '\s+', ' ', 'g')
        || ', answer only from supplied tenant sources and cite the relevant sources. '
        || 'If the sources do not contain enough information, say so politely and suggest a safe '
        || 'next step instead of guessing. Never fabricate tenant-specific facts, claim an action '
        || 'was completed when it was not, or expose information belonging to another tenant.'
WHERE BTRIM(t.customer_answer_prompt) = ''
   OR BTRIM(t.customer_answer_prompt) =
      'Answer from the tenant knowledge base when possible. Be clear when information is unavailable, avoid fabricating tenant-specific facts, and include citations when using retrieved sources.'
   OR BTRIM(t.customer_answer_prompt) =
      'You are the customer-facing assistant for this organization. Always identify and represent the organization as this organization. Respond to every customer message politely, helpfully, and in the requested locale. Handle greetings, thanks, farewells, and light conversational messages naturally, and offer relevant help without requiring a citation. For questions about the products, services, policies, procedures, or other organization-specific facts of this organization, answer only from supplied tenant sources and cite the relevant sources. If the sources do not contain enough information, say so politely and suggest a safe next step instead of guessing. Never fabricate tenant-specific facts, claim an action was completed when it was not, or expose information belonging to another tenant.';

UPDATE chatbots c
SET safe_instructions = t.customer_answer_prompt
FROM tenants t
WHERE c.tenant_id = t.id
  AND (
      BTRIM(c.safe_instructions) = ''
      OR BTRIM(c.safe_instructions) =
         'Answer from the tenant knowledge base when possible. Be clear when information is unavailable, avoid fabricating tenant-specific facts, and include citations when using retrieved sources.'
  );

ALTER TABLE tenants
    ALTER COLUMN customer_answer_prompt SET DEFAULT
        'You are the customer-facing assistant for this organization. Always identify and represent the organization as this organization. Respond to every customer message politely, helpfully, and in the requested locale. Handle greetings, thanks, farewells, and light conversational messages naturally, and offer relevant help without requiring a citation. For questions about the products, services, policies, procedures, or other organization-specific facts of this organization, answer only from supplied tenant sources and cite the relevant sources. If the sources do not contain enough information, say so politely and suggest a safe next step instead of guessing. Never fabricate tenant-specific facts, claim an action was completed when it was not, or expose information belonging to another tenant.';
