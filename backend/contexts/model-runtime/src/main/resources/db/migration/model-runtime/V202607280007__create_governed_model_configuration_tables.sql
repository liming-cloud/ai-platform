-- 所属上下文：model-runtime。目的：持久化平台受控目录、三层模型配置和加密凭据。
-- 数据影响：新增三张事实表；仅可前向迁移，回滚须先确认不存在依赖配置的数据。

CREATE TABLE model_runtime.controlled_model_catalog_entry (
    id BIGINT PRIMARY KEY,
    provider_kind VARCHAR(48) NOT NULL,
    endpoint VARCHAR(2048) NOT NULL,
    model_code VARCHAR(320) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_controlled_model_catalog_provider_kind
        CHECK (provider_kind IN ('DETERMINISTIC', 'OPENAI_COMPATIBLE', 'OLLAMA')),
    CONSTRAINT ck_controlled_model_catalog_endpoint
        CHECK (
            endpoint LIKE 'https://%'
            AND position('@' IN endpoint) = 0
            AND position('?' IN endpoint) = 0
            AND position('#' IN endpoint) = 0
        ),
    CONSTRAINT ck_controlled_model_catalog_model_type
        CHECK (model_type IN ('CHAT', 'EMBEDDING')),
    CONSTRAINT ck_controlled_model_catalog_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT ck_controlled_model_catalog_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_controlled_model_catalog_entry_unique
    ON model_runtime.controlled_model_catalog_entry (
        provider_kind,
        endpoint,
        model_code,
        model_type
    );

CREATE TABLE model_runtime.model_configuration (
    id BIGINT PRIMARY KEY,
    scope VARCHAR(32) NOT NULL,
    tenant_id BIGINT,
    owner_membership_id BIGINT,
    display_name VARCHAR(320) NOT NULL,
    catalog_entry_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_model_configuration_catalog_entry
        FOREIGN KEY (catalog_entry_id)
        REFERENCES model_runtime.controlled_model_catalog_entry (id),
    CONSTRAINT ck_model_configuration_scope
        CHECK (scope IN ('PLATFORM', 'TENANT', 'PERSONAL')),
    CONSTRAINT ck_model_configuration_ownership
        CHECK (
            (scope = 'PLATFORM' AND tenant_id IS NULL AND owner_membership_id IS NULL)
            OR (scope = 'TENANT' AND tenant_id IS NOT NULL AND owner_membership_id IS NULL)
            OR (scope = 'PERSONAL' AND tenant_id IS NOT NULL AND owner_membership_id IS NOT NULL)
        ),
    CONSTRAINT ck_model_configuration_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED')),
    CONSTRAINT ck_model_configuration_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_model_configuration_platform_name
    ON model_runtime.model_configuration (lower(display_name))
    WHERE scope = 'PLATFORM';

CREATE UNIQUE INDEX uk_model_configuration_tenant_name
    ON model_runtime.model_configuration (tenant_id, lower(display_name))
    WHERE scope = 'TENANT';

CREATE UNIQUE INDEX uk_model_configuration_personal_name
    ON model_runtime.model_configuration (tenant_id, owner_membership_id, lower(display_name))
    WHERE scope = 'PERSONAL';

CREATE INDEX idx_model_configuration_scope_owner
    ON model_runtime.model_configuration (scope, tenant_id, owner_membership_id, created_at DESC, id DESC);

CREATE TABLE model_runtime.model_configuration_credential (
    model_configuration_id BIGINT PRIMARY KEY,
    algorithm VARCHAR(32) NOT NULL,
    master_key_version VARCHAR(64) NOT NULL,
    data_key_nonce BYTEA NOT NULL,
    encrypted_data_key BYTEA NOT NULL,
    ciphertext_nonce BYTEA NOT NULL,
    ciphertext BYTEA NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    rotated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_model_configuration_credential_configuration
        FOREIGN KEY (model_configuration_id)
        REFERENCES model_runtime.model_configuration (id),
    CONSTRAINT ck_model_configuration_credential_algorithm
        CHECK (algorithm = 'AES-256-GCM'),
    CONSTRAINT ck_model_configuration_credential_nonce
        CHECK (octet_length(data_key_nonce) = 12 AND octet_length(ciphertext_nonce) = 12),
    CONSTRAINT ck_model_configuration_credential_ciphertext
        CHECK (octet_length(encrypted_data_key) > 0 AND octet_length(ciphertext) > 0),
    CONSTRAINT ck_model_configuration_credential_fingerprint
        CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);
