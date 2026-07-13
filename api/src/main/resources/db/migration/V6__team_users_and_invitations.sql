ALTER TABLE invitations RENAME COLUMN token TO token_hash;

ALTER TABLE invitations
    ADD COLUMN last_sent_at TIMESTAMP,
    ADD COLUMN accepted_at TIMESTAMP;

UPDATE invitations SET last_sent_at = created_at WHERE last_sent_at IS NULL;
ALTER TABLE invitations ALTER COLUMN last_sent_at SET NOT NULL;

-- Keep pre-migration invitation links valid while ensuring raw tokens are no longer stored.
UPDATE invitations
SET token_hash = RTRIM(TRANSLATE(ENCODE(DIGEST(token_hash, 'sha256'), 'base64'), '+/', '-_'), '=');

WITH duplicate_pending AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY tenant_id, LOWER(email) ORDER BY created_at DESC, id DESC
    ) AS row_number
    FROM invitations
    WHERE status = 'PENDING'
)
UPDATE invitations
SET status = 'CANCELLED'
WHERE id IN (SELECT id FROM duplicate_pending WHERE row_number > 1);

DROP INDEX IF EXISTS idx_invitations_token;
CREATE UNIQUE INDEX idx_invitations_token_hash ON invitations(token_hash);
CREATE INDEX idx_invitations_tenant_email ON invitations(tenant_id, LOWER(email));
CREATE UNIQUE INDEX uq_invitations_pending_tenant_email
    ON invitations(tenant_id, LOWER(email))
    WHERE status = 'PENDING';
