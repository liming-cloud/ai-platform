-- 所属上下文：model-runtime。目的：为 ADR-0066 保存平台范围部署及能力验证事实。
-- 影响：新增仅平台拥有的部署与验证表；不修改租户部署、发现或目录表。回滚限制：删除将丢失平台供应链审计事实。
CREATE TABLE model_runtime.platform_model_deployment (
    id BIGINT PRIMARY KEY,
    scope VARCHAR(16) NOT NULL,
    platform_provider_connection_id BIGINT NOT NULL REFERENCES model_runtime.platform_provider_connection (id),
    platform_provider_connection_revision BIGINT NOT NULL,
    model_code VARCHAR(200) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    capabilities TEXT[] NOT NULL,
    context_window INTEGER NOT NULL,
    output_limit INTEGER NULL,
    embedding_dimension INTEGER NULL,
    last_verification_id BIGINT NULL,
    verification_expires_at TIMESTAMPTZ NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_platform_model_deployment_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_model_deployment_revision CHECK (platform_provider_connection_revision >= 0 AND version >= 0),
    CONSTRAINT ck_platform_model_deployment_type CHECK (model_type IN ('CHAT', 'EMBEDDING')),
    CONSTRAINT ck_platform_model_deployment_status CHECK (status IN ('DRAFT', 'VERIFIED', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT ck_platform_model_deployment_verification CHECK (
        (status = 'DRAFT' AND last_verification_id IS NULL AND verification_expires_at IS NULL)
        OR (status = 'DISABLED')
        OR (status IN ('VERIFIED', 'PUBLISHED') AND last_verification_id IS NOT NULL AND verification_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_platform_model_deployment_type_contract CHECK (
        (model_type = 'CHAT' AND 'CHAT' = ANY(capabilities) AND output_limit IS NOT NULL AND output_limit > 0 AND embedding_dimension IS NULL)
        OR (model_type = 'EMBEDDING' AND 'EMBEDDING' = ANY(capabilities) AND output_limit IS NULL AND embedding_dimension IS NOT NULL AND embedding_dimension > 0)
    )
);
CREATE UNIQUE INDEX uk_platform_model_deployment_name ON model_runtime.platform_model_deployment (lower(display_name));
CREATE INDEX idx_platform_model_deployment_created ON model_runtime.platform_model_deployment (created_at DESC, id DESC);
CREATE TABLE model_runtime.platform_model_deployment_request (
    idempotency_key_hash CHAR(64) PRIMARY KEY,
    request_fingerprint CHAR(64) NOT NULL,
    platform_model_deployment_id BIGINT NOT NULL REFERENCES model_runtime.platform_model_deployment (id)
);
CREATE TABLE model_runtime.platform_model_capability_verification (
    id BIGINT PRIMARY KEY,
    platform_model_deployment_id BIGINT NOT NULL REFERENCES model_runtime.platform_model_deployment (id),
    deployment_revision BIGINT NOT NULL,
    platform_provider_connection_id BIGINT NOT NULL REFERENCES model_runtime.platform_provider_connection (id),
    platform_provider_connection_revision BIGINT NOT NULL,
    model_code VARCHAR(200) NOT NULL,
    declared_capabilities TEXT[] NOT NULL,
    observed_capabilities TEXT[] NOT NULL,
    succeeded BOOLEAN NOT NULL,
    result_code VARCHAR(120) NOT NULL,
    elapsed_millis BIGINT NOT NULL,
    probed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NULL,
    CONSTRAINT ck_platform_model_capability_verification_revision CHECK (deployment_revision >= 0 AND platform_provider_connection_revision >= 0),
    CONSTRAINT ck_platform_model_capability_verification_elapsed CHECK (elapsed_millis >= 0),
    CONSTRAINT ck_platform_model_capability_verification_outcome CHECK (
        (succeeded = TRUE AND expires_at IS NOT NULL AND expires_at > probed_at)
        OR (succeeded = FALSE AND expires_at IS NULL)
    )
);
ALTER TABLE model_runtime.platform_model_deployment
    ADD CONSTRAINT fk_platform_model_deployment_verification
    FOREIGN KEY (last_verification_id) REFERENCES model_runtime.platform_model_capability_verification (id);
