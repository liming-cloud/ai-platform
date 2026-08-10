-- 所属上下文：model-runtime。目的：持久化不可变厂商模型发现事实；不保存 Endpoint、凭据或原始响应。
-- 影响：新增发现根和候选模型明细；可回滚为删除两张空表。
CREATE TABLE model_runtime.provider_model_discovery (
    id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, provider_connection_id BIGINT NOT NULL,
    provider_connection_revision BIGINT NOT NULL, succeeded BOOLEAN NOT NULL, result_code VARCHAR(120) NOT NULL,
    elapsed_millis BIGINT NOT NULL, discovered_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ,
    CONSTRAINT fk_provider_model_discovery_connection FOREIGN KEY (tenant_id, provider_connection_id)
        REFERENCES model_runtime.provider_connection (tenant_id, id),
    CONSTRAINT ck_provider_model_discovery_result CHECK ((succeeded AND expires_at > discovered_at) OR (NOT succeeded AND expires_at IS NULL))
);
CREATE TABLE model_runtime.provider_discovered_model (
    discovery_id BIGINT NOT NULL REFERENCES model_runtime.provider_model_discovery (id),
    model_code VARCHAR(200) NOT NULL, model_type VARCHAR(32) NOT NULL, capabilities VARCHAR(80)[] NOT NULL,
    PRIMARY KEY (discovery_id, model_code, model_type)
);
CREATE INDEX idx_provider_model_discovery_connection ON model_runtime.provider_model_discovery (tenant_id, provider_connection_id, discovered_at DESC);
