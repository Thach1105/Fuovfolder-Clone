-- Broadcast configuration rules (admin-managed)
CREATE TABLE broadcast_configs (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  varchar(64)  NOT NULL,
    config_json jsonb        NOT NULL DEFAULT '{}',
    enabled     boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_broadcast_configs_event_type
    ON broadcast_configs(event_type);

-- Audit log of broadcast events sent
CREATE TABLE broadcast_events (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  varchar(64)  NOT NULL,
    message     text         NOT NULL,
    data_json   jsonb        NOT NULL DEFAULT '{}',
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX ix_broadcast_events_type_created
    ON broadcast_events(event_type, created_at DESC);
