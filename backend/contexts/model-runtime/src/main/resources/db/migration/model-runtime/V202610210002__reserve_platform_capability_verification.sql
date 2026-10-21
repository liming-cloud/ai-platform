-- model-runtime：外呼前冻结本次整轮预算，外呼后原子记录三样本与安全审计。
-- 不更新历史部署、连接或验证，不保存密钥、正文、向量及原始错误。
-- 预留不是实际扣费或月度账单；失败、超时与未知用量均保留金额，不能自动退款。
-- 无破坏性数据迁移；回退须先停止新协议并保留已有预留与审计，禁止在线删事实。
CREATE TABLE model_runtime.platform_capability_verification_reservation (
    id BIGINT PRIMARY KEY,
    deployment_id BIGINT NOT NULL REFERENCES model_runtime.platform_model_deployment(id),
    deployment_revision BIGINT NOT NULL CHECK (deployment_revision >= 0),
    actor_subject_id BIGINT NOT NULL CHECK (actor_subject_id > 0),
    actor_permission_version BIGINT NOT NULL CHECK (actor_permission_version >= 0),
    target_fingerprint VARCHAR(64) NOT NULL CHECK (target_fingerprint ~ '^[0-9a-f]{64}$'),
    inputs_digest VARCHAR(64) NOT NULL CHECK (inputs_digest ~ '^[0-9a-f]{64}$'),
    plan_fingerprint VARCHAR(64) NOT NULL UNIQUE CHECK (plan_fingerprint ~ '^[0-9a-f]{64}$'),
    quote_digest VARCHAR(64) NOT NULL CHECK (quote_digest ~ '^[0-9a-f]{64}$'),
    quote_expires_at TIMESTAMPTZ NOT NULL,
    sample_version VARCHAR(80) NOT NULL,
    expected_sample_digests TEXT[] NOT NULL CHECK (cardinality(expected_sample_digests) = 3),
    reserved_fee NUMERIC(20,9) NOT NULL CHECK (reserved_fee >= 0 AND reserved_fee <= 0.03),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'TIMED_OUT')),
    outcome VARCHAR(40),
    attempted_requests INTEGER CHECK (attempted_requests BETWEEN 0 AND 3),
    passed_sample_digests TEXT[],
    started_at TIMESTAMPTZ NOT NULL,
    deadline TIMESTAMPTZ NOT NULL CHECK (deadline > started_at AND deadline <= started_at + INTERVAL '30 seconds'),
    completed_at TIMESTAMPTZ,
    CHECK (status <> 'COMPLETED' OR (outcome IS NOT NULL AND attempted_requests IS NOT NULL AND passed_sample_digests IS NOT NULL AND completed_at IS NOT NULL)),
    CHECK (outcome IS DISTINCT FROM 'PASSED' OR (status = 'COMPLETED' AND attempted_requests = 3 AND passed_sample_digests = expected_sample_digests))
);
CREATE UNIQUE INDEX platform_capability_verification_one_active
    ON model_runtime.platform_capability_verification_reservation(deployment_id) WHERE status = 'ACTIVE';

CREATE TABLE model_runtime.platform_model_deployment_verification_audit (
    reservation_id BIGINT PRIMARY KEY REFERENCES model_runtime.platform_capability_verification_reservation(id),
    verification_id BIGINT NOT NULL UNIQUE REFERENCES model_runtime.platform_model_capability_verification(id),
    deployment_id BIGINT NOT NULL REFERENCES model_runtime.platform_model_deployment(id),
    actor_subject_id BIGINT NOT NULL,
    actor_permission_version BIGINT NOT NULL,
    succeeded BOOLEAN NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
