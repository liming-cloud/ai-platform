-- 所属上下文：model-runtime。目的：把指定模型的真实能力验证固化为不可变事实，并禁止旧声明继续充当发布证明。
-- 影响：历史 VERIFIED/PUBLISHED 部署回退为 DRAFT，必须重新探测后发布；本迁移不自动伪造供应商调用结果，回滚只能人工删除新增事实并恢复业务状态。
ALTER TABLE model_runtime.model_deployment
    ADD COLUMN last_verification_id BIGINT,
    ADD COLUMN verification_expires_at TIMESTAMPTZ;

UPDATE model_runtime.model_deployment
SET status = 'DRAFT',
    version = version + 1
WHERE status IN ('VERIFIED', 'PUBLISHED');

CREATE TABLE model_runtime.model_capability_verification (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    model_deployment_id BIGINT NOT NULL,
    model_deployment_version BIGINT NOT NULL,
    provider_connection_id BIGINT NOT NULL,
    provider_connection_revision BIGINT NOT NULL,
    model_code VARCHAR(160) NOT NULL,
    declared_capabilities VARCHAR(80)[] NOT NULL,
    observed_capabilities VARCHAR(80)[] NOT NULL,
    succeeded BOOLEAN NOT NULL,
    result_code VARCHAR(120) NOT NULL,
    elapsed_millis BIGINT NOT NULL,
    probed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    CONSTRAINT fk_model_capability_verification_deployment
        FOREIGN KEY (tenant_id, model_deployment_id)
        REFERENCES model_runtime.model_deployment (tenant_id, id),
    CONSTRAINT ck_model_capability_verification_version
        CHECK (model_deployment_version >= 0 AND provider_connection_revision >= 0),
    CONSTRAINT ck_model_capability_verification_elapsed
        CHECK (elapsed_millis >= 0),
    CONSTRAINT ck_model_capability_verification_expiry
        CHECK ((succeeded IS TRUE AND expires_at > probed_at)
            OR (succeeded IS FALSE AND expires_at IS NULL))
);

ALTER TABLE model_runtime.model_deployment
    ADD CONSTRAINT fk_model_deployment_last_verification
        FOREIGN KEY (last_verification_id)
        REFERENCES model_runtime.model_capability_verification (id),
    ADD CONSTRAINT ck_model_deployment_verification
        CHECK ((status = 'DRAFT' AND last_verification_id IS NULL AND verification_expires_at IS NULL)
            OR (status IN ('VERIFIED', 'PUBLISHED')
                AND last_verification_id IS NOT NULL
                AND verification_expires_at IS NOT NULL));

CREATE INDEX idx_model_capability_verification_deployment_probed
    ON model_runtime.model_capability_verification (
        tenant_id, model_deployment_id, probed_at DESC, id DESC);
