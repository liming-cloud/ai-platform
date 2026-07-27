CREATE SCHEMA IF NOT EXISTS model_runtime;

CREATE TABLE model_runtime.provider_connection (
    tenant_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    provider_kind VARCHAR(48) NOT NULL,
    display_name VARCHAR(320) NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    region VARCHAR(320) NOT NULL,
    authentication_type VARCHAR(32) NOT NULL,
    credential_reference VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    last_probe_succeeded BOOLEAN,
    last_probe_code VARCHAR(120),
    last_probed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_provider_connection PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_provider_connection_kind
        CHECK (provider_kind IN ('DETERMINISTIC', 'OPENAI_COMPATIBLE', 'OLLAMA')),
    CONSTRAINT ck_provider_connection_authentication
        CHECK (authentication_type IN ('API_KEY')),
    CONSTRAINT ck_provider_connection_status
        CHECK (status IN ('DRAFT', 'VERIFIED', 'DISABLED')),
    CONSTRAINT ck_provider_connection_version
        CHECK (version >= 0),
    CONSTRAINT ck_provider_connection_probe_completeness
        CHECK (
            (last_probe_succeeded IS NULL
                AND last_probe_code IS NULL
                AND last_probed_at IS NULL)
            OR
            (last_probe_succeeded IS NOT NULL
                AND last_probe_code IS NOT NULL
                AND last_probed_at IS NOT NULL)
        ),
    CONSTRAINT ck_provider_connection_verified_probe
        CHECK (status <> 'VERIFIED' OR last_probe_succeeded IS TRUE),
    CONSTRAINT ck_provider_connection_draft_probe
        CHECK (status <> 'DRAFT' OR last_probe_succeeded IS DISTINCT FROM TRUE)
);

CREATE UNIQUE INDEX uk_provider_connection_tenant_name
    ON model_runtime.provider_connection (tenant_id, lower(display_name));

CREATE INDEX idx_provider_connection_tenant_created
    ON model_runtime.provider_connection (tenant_id, created_at DESC, id DESC);

CREATE TABLE model_runtime.provider_connection_request (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    provider_connection_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_provider_connection_request_tenant_key
        UNIQUE (tenant_id, idempotency_key_hash),
    CONSTRAINT fk_provider_connection_request_connection
        FOREIGN KEY (tenant_id, provider_connection_id)
        REFERENCES model_runtime.provider_connection (tenant_id, id)
);
