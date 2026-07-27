CREATE TABLE IF NOT EXISTS app_settings (
    setting_key varchar(120) PRIMARY KEY,
    setting_value text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
