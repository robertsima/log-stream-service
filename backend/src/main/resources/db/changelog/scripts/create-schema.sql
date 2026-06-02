-- 01-create-db-and-tables.sql
-- PostgreSQL init script for Log Stream Service.
--
-- Database is created via POSTGRES_DB=appdb environment variable in the Podman pod.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    email VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS apps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    owner_user_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_apps_owner_user
        FOREIGN KEY (owner_user_id)
        REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT uk_apps_owner_name
        UNIQUE (owner_user_id, name)
);

CREATE TABLE IF NOT EXISTS app_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    app_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,

    -- Store only a hash of the full token. Never store the raw token.
    token_hash VARCHAR(255) NOT NULL,

    -- Store a short prefix so users can identify tokens later.
    -- Example: lss_live_abc123
    token_prefix VARCHAR(50) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT fk_app_tokens_app
        FOREIGN KEY (app_id)
        REFERENCES apps (id)
        ON DELETE CASCADE,

    CONSTRAINT uk_app_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_users_email
    ON users (email);

CREATE INDEX IF NOT EXISTS idx_apps_owner_user_id
    ON apps (owner_user_id);

CREATE INDEX IF NOT EXISTS idx_app_tokens_app_id
    ON app_tokens (app_id);

CREATE INDEX IF NOT EXISTS idx_app_tokens_token_prefix
    ON app_tokens (token_prefix);

CREATE INDEX IF NOT EXISTS idx_app_tokens_active
    ON app_tokens (app_id, revoked_at, expires_at);