CREATE TABLE IF NOT EXISTS model_provider (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    provider_name VARCHAR(128) NOT NULL,
    provider_type VARCHAR(64) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    api_key_ciphertext TEXT NOT NULL,
    api_key_last4 VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    budget_usd NUMERIC(12, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_model_provider_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT uk_model_provider_tenant_name UNIQUE (tenant_id, provider_name)
);

CREATE INDEX IF NOT EXISTS idx_model_provider_tenant ON model_provider (tenant_id);

CREATE TABLE IF NOT EXISTS model_catalog (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    model_code VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    capability_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    context_window INTEGER,
    max_output_tokens INTEGER,
    embedding_dimensions INTEGER,
    default_temperature NUMERIC(6, 4),
    default_top_p NUMERIC(6, 4),
    default_top_k INTEGER,
    default_frequency_penalty NUMERIC(6, 4),
    default_presence_penalty NUMERIC(6, 4),
    default_reasoning_effort VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_model_catalog_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT fk_model_catalog_provider FOREIGN KEY (provider_id) REFERENCES model_provider (id),
    CONSTRAINT uk_model_catalog_tenant_provider_model_capability UNIQUE
        (tenant_id, provider_id, model_code, capability_type)
);

CREATE INDEX IF NOT EXISTS idx_model_catalog_tenant_provider ON model_catalog (tenant_id, provider_id);
CREATE INDEX IF NOT EXISTS idx_model_catalog_tenant_capability ON model_catalog (tenant_id, capability_type);

CREATE TABLE IF NOT EXISTS model_invocation_record (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    provider_type VARCHAR(64) NOT NULL,
    model_code VARCHAR(128) NOT NULL,
    capability_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    latency_ms BIGINT NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    error_code VARCHAR(64),
    error_message VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_model_invocation_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT fk_model_invocation_provider FOREIGN KEY (provider_id) REFERENCES model_provider (id),
    CONSTRAINT fk_model_invocation_model FOREIGN KEY (model_id) REFERENCES model_catalog (id)
);

CREATE INDEX IF NOT EXISTS idx_model_invocation_tenant_created
    ON model_invocation_record (tenant_id, created_at DESC);
