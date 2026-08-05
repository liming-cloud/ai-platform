-- 所属上下文：evaluation-operations；目的：持久化私有评测数据集的不可变清单；影响：新增只含对象引用和摘要的元数据表；回滚：确认无评测运行引用后删除该表，私有对象由独立保留策略清理。
CREATE SCHEMA IF NOT EXISTS evaluation_operations;

CREATE TABLE evaluation_operations.evaluation_dataset (
    tenant_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    private_content_reference VARCHAR(1024) NOT NULL,
    content_digest CHAR(64) NOT NULL,
    entry_count INTEGER NOT NULL,
    classification VARCHAR(32) NOT NULL,
    retention_policy_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_evaluation_dataset PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_evaluation_dataset_private_reference
        CHECK (private_content_reference LIKE 'private://evaluation/%'),
    CONSTRAINT ck_evaluation_dataset_digest
        CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evaluation_dataset_entry_count CHECK (entry_count > 0),
    CONSTRAINT ck_evaluation_dataset_classification CHECK (classification = 'SYNTHETIC'),
    CONSTRAINT ck_evaluation_dataset_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_evaluation_dataset_version CHECK (version >= 0)
);

CREATE INDEX ix_evaluation_dataset_tenant_created
    ON evaluation_operations.evaluation_dataset (tenant_id, created_at DESC, id DESC);
