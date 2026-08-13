-- 所属上下文：model-runtime。目的：为 ADR-0066 建立与租户连接完全隔离的平台范围连接、幂等与密文凭据存储。
-- 影响：新增平台专用表；不修改租户连接、部署或凭据表。回滚限制：删除将丢失平台连接与凭据审计事实。
CREATE TABLE model_runtime.platform_provider_connection (
    id BIGINT PRIMARY KEY,
    scope VARCHAR(16) NOT NULL,
    provider_kind VARCHAR(64) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    endpoint TEXT NOT NULL,
    region VARCHAR(80) NOT NULL,
    authentication_type VARCHAR(32) NOT NULL,
    credential_reference TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    last_probe_succeeded BOOLEAN NULL,
    last_probe_code VARCHAR(120) NULL,
    last_probed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_platform_provider_connection_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_provider_connection_kind CHECK (provider_kind IN ('OPENAI_COMPATIBLE', 'DETERMINISTIC')),
    CONSTRAINT ck_platform_provider_connection_authentication CHECK (authentication_type = 'API_KEY'),
    CONSTRAINT ck_platform_provider_connection_status CHECK (status IN ('DRAFT', 'VERIFIED', 'DISABLED')),
    CONSTRAINT ck_platform_provider_connection_version CHECK (version >= 0),
    CONSTRAINT ck_platform_provider_connection_probe_completeness CHECK (
        (last_probe_succeeded IS NULL AND last_probe_code IS NULL AND last_probed_at IS NULL)
        OR (last_probe_succeeded IS NOT NULL AND last_probe_code IS NOT NULL AND last_probed_at IS NOT NULL)
    ),
    CONSTRAINT ck_platform_provider_connection_verified_probe CHECK (
        status <> 'VERIFIED' OR last_probe_succeeded = TRUE
    ),
    CONSTRAINT ck_platform_provider_connection_draft_probe CHECK (
        status <> 'DRAFT' OR last_probe_succeeded IS DISTINCT FROM TRUE
    ),
    CONSTRAINT ck_platform_provider_connection_credential_reference CHECK (
        credential_reference ~ '^secret://platform-provider-connection/[1-9][0-9]{0,18}$'
    )
);

CREATE UNIQUE INDEX uk_platform_provider_connection_name
    ON model_runtime.platform_provider_connection (lower(display_name));
CREATE INDEX idx_platform_provider_connection_created
    ON model_runtime.platform_provider_connection (created_at DESC, id DESC);

CREATE TABLE model_runtime.platform_provider_connection_request (
    idempotency_key_hash CHAR(64) PRIMARY KEY,
    request_fingerprint CHAR(64) NOT NULL,
    platform_provider_connection_id BIGINT NOT NULL REFERENCES model_runtime.platform_provider_connection (id)
);

CREATE TABLE model_runtime.platform_provider_connection_credential (
    platform_provider_connection_id BIGINT PRIMARY KEY
        REFERENCES model_runtime.platform_provider_connection (id),
    algorithm VARCHAR(64) NOT NULL,
    master_key_version VARCHAR(120) NOT NULL,
    data_key_nonce BYTEA NOT NULL,
    encrypted_data_key BYTEA NOT NULL,
    ciphertext_nonce BYTEA NOT NULL,
    ciphertext BYTEA NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    rotated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_platform_provider_connection_credential_algorithm
        CHECK (algorithm = 'AES/GCM/NoPadding'),
    CONSTRAINT ck_platform_provider_connection_credential_material
        CHECK (octet_length(data_key_nonce) > 0 AND octet_length(encrypted_data_key) > 0
            AND octet_length(ciphertext_nonce) > 0 AND octet_length(ciphertext) > 0)
);
