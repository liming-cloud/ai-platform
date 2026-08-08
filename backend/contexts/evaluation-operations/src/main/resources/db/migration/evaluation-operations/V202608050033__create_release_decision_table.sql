-- 所属上下文：evaluation-operations；目的：持久化由冻结评测运行唯一导出的发布决定；影响：新增无正文发布决定表；回滚：确认没有助手版本引用后删除该表。
CREATE TABLE evaluation_operations.release_decision (
    tenant_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    evaluation_run_id BIGINT NOT NULL,
    assistant_version_id BIGINT NOT NULL,
    assistant_version_revision BIGINT NOT NULL,
    policy_version VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_release_decision PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_release_decision_run UNIQUE (tenant_id, evaluation_run_id),
    CONSTRAINT fk_release_decision_run FOREIGN KEY (tenant_id, evaluation_run_id)
        REFERENCES evaluation_operations.evaluation_run (tenant_id, id),
    CONSTRAINT ck_release_decision_version CHECK (version >= 0),
    CONSTRAINT ck_release_decision_status CHECK (status IN ('AUTHORIZED', 'REJECTED')),
    CONSTRAINT ck_release_decision_reason CHECK (reason_code IN ('QUALITY_GATE_PASSED', 'QUALITY_GATE_FAILED')),
    CONSTRAINT ck_release_decision_status_reason CHECK (
        (status = 'AUTHORIZED' AND reason_code = 'QUALITY_GATE_PASSED')
        OR (status = 'REJECTED' AND reason_code = 'QUALITY_GATE_FAILED')
    )
);

CREATE INDEX ix_release_decision_tenant_decided
    ON evaluation_operations.release_decision (tenant_id, decided_at DESC, id DESC);
