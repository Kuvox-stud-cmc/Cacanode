ALTER TABLE widget_configs
    ADD COLUMN managed_widget_token_id UUID REFERENCES integration_tokens(id) ON DELETE SET NULL,
    ADD COLUMN encrypted_widget_token_secret TEXT;

CREATE INDEX idx_widget_config_managed_token ON widget_configs(managed_widget_token_id);
