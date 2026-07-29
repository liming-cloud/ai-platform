-- 所属上下文：知识数据。目的：保存 M3 同步产物的显式发布历史，供类型化索引快照选择；不改写 M1 文本发布事实。
-- 数据影响：新增空表；不可回滚，生产环境只能以前向迁移保留发布审计。
CREATE TABLE knowledge_data.published_sync_artifact (
    tenant_id BIGINT NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    source_version BIGINT NOT NULL,
    artifact_id BIGINT NOT NULL,
    quality_status VARCHAR(16) NOT NULL,
    rule_set_version VARCHAR(120) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_published_sync_artifact PRIMARY KEY (
        tenant_id, knowledge_space_id, data_source_id, source_version
    ),
    CONSTRAINT fk_published_sync_artifact_source FOREIGN KEY (
        tenant_id, knowledge_space_id, data_source_id
    ) REFERENCES knowledge_data.governed_data_source (tenant_id, knowledge_space_id, id),
    CONSTRAINT fk_published_sync_artifact_artifact FOREIGN KEY (
        tenant_id, knowledge_space_id, artifact_id
    ) REFERENCES knowledge_data.sync_artifact (tenant_id, knowledge_space_id, id),
    CONSTRAINT ck_published_sync_artifact_version CHECK (source_version > 0),
    CONSTRAINT ck_published_sync_artifact_quality CHECK (quality_status IN ('PASS', 'WARN'))
);

CREATE INDEX ix_published_sync_artifact_current
    ON knowledge_data.published_sync_artifact (
        tenant_id, knowledge_space_id, data_source_id, source_version DESC
    );
