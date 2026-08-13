-- 所属上下文：model-runtime。目的：为 ADR-0066 保存不带租户标识的平台模型发现事实及候选。
-- 影响：新增平台专用发现表；不修改租户发现表。回滚限制：删除将丢失平台发现审计事实。
CREATE TABLE model_runtime.platform_provider_model_discovery (
    id BIGINT PRIMARY KEY,
    platform_provider_connection_id BIGINT NOT NULL
        REFERENCES model_runtime.platform_provider_connection (id),
    platform_provider_connection_revision BIGINT NOT NULL,
    succeeded BOOLEAN NOT NULL,
    result_code VARCHAR(120) NOT NULL,
    elapsed_millis BIGINT NOT NULL,
    discovered_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NULL,
    CONSTRAINT ck_platform_provider_model_discovery_revision CHECK (
        platform_provider_connection_revision >= 0
    ),
    CONSTRAINT ck_platform_provider_model_discovery_elapsed CHECK (elapsed_millis >= 0),
    CONSTRAINT ck_platform_provider_model_discovery_outcome CHECK (
        (succeeded = TRUE AND expires_at IS NOT NULL AND expires_at > discovered_at)
        OR (succeeded = FALSE AND expires_at IS NULL)
    )
);

CREATE INDEX idx_platform_provider_model_discovery_connection
    ON model_runtime.platform_provider_model_discovery
        (platform_provider_connection_id, discovered_at DESC, id DESC);

CREATE TABLE model_runtime.platform_discovered_model (
    discovery_id BIGINT NOT NULL
        REFERENCES model_runtime.platform_provider_model_discovery (id),
    model_code VARCHAR(200) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    capabilities TEXT[] NOT NULL,
    PRIMARY KEY (discovery_id, model_code, model_type),
    CONSTRAINT ck_platform_discovered_model_type CHECK (model_type IN ('CHAT', 'EMBEDDING'))
);
