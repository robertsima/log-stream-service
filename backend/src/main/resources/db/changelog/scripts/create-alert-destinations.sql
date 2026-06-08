CREATE TABLE IF NOT EXISTS alert_destinations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    app_id UUID NOT NULL,
    destination_type VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    webhook_url TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_alert_destinations_app
        FOREIGN KEY (app_id)
        REFERENCES apps (id)
        ON DELETE CASCADE,

    CONSTRAINT ck_alert_destinations_type
        CHECK (destination_type IN ('DISCORD', 'SLACK'))
);

CREATE INDEX IF NOT EXISTS idx_alert_destinations_app_id
    ON alert_destinations (app_id);

CREATE INDEX IF NOT EXISTS idx_alert_destinations_app_enabled
    ON alert_destinations (app_id, enabled);