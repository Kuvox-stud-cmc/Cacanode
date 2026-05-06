-- Add remember-me persistence flag for refresh token rotation behavior.
-- Needed for existing databases where V1 did not include this column.
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS persistent BOOLEAN;

UPDATE refresh_tokens
SET persistent = FALSE
WHERE persistent IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN persistent SET DEFAULT FALSE;

ALTER TABLE refresh_tokens
    ALTER COLUMN persistent SET NOT NULL;
