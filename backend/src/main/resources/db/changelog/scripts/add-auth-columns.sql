ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'USER',
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(32) DEFAULT 'firebase',
    ADD COLUMN IF NOT EXISTS auth_subject VARCHAR(255);

UPDATE users
SET role = 'USER'
WHERE role IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_auth_provider_subject
    ON users (auth_provider, auth_subject)
    WHERE auth_provider IS NOT NULL AND auth_subject IS NOT NULL;
