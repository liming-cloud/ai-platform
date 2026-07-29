-- 所属上下文：知识数据。目的：为 M3 受治理来源、不可变修订、同步产物和质量结论建立独立事实表；不影响 M1 文本数据源。
-- 数据影响：新增空表；不可回滚，生产环境应以新的 Flyway 前向迁移替代删除。
CREATE TABLE knowledge_data.governed_data_source (
    tenant_id BIGINT NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    name VARCHAR(320) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_governed_data_source PRIMARY KEY (id),
    CONSTRAINT uk_governed_data_source_tenant_space_id UNIQUE (tenant_id, knowledge_space_id, id),
    CONSTRAINT fk_governed_data_source_space FOREIGN KEY (tenant_id, knowledge_space_id)
        REFERENCES knowledge_data.knowledge_space (tenant_id, id),
    CONSTRAINT ck_governed_data_source_type CHECK (source_type IN ('FILE', 'OBJECT_STORAGE', 'STRUCTURED_DATASET')),
    CONSTRAINT ck_governed_data_source_status CHECK (status IN ('DRAFT', 'PROBING', 'READY', 'SYNCING', 'FAILED', 'RETIRED')),
    CONSTRAINT ck_governed_data_source_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_governed_data_source_space_name
    ON knowledge_data.governed_data_source (tenant_id, knowledge_space_id, lower(name));

CREATE TABLE knowledge_data.source_revision (
    tenant_id BIGINT NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    definition_digest CHAR(64) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_source_revision PRIMARY KEY (tenant_id, knowledge_space_id, data_source_id, revision),
    CONSTRAINT fk_source_revision_source FOREIGN KEY (tenant_id, knowledge_space_id, data_source_id)
        REFERENCES knowledge_data.governed_data_source (tenant_id, knowledge_space_id, id),
    CONSTRAINT ck_source_revision_positive CHECK (revision > 0),
    CONSTRAINT ck_source_revision_type CHECK (source_type IN ('FILE', 'OBJECT_STORAGE', 'STRUCTURED_DATASET')),
    CONSTRAINT ck_source_revision_digest CHECK (definition_digest ~ '^[0-9a-f]{64}$')
);

CREATE TABLE knowledge_data.sync_artifact (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    source_revision BIGINT NOT NULL,
    content_digest CHAR(64) NOT NULL,
    record_count BIGINT NOT NULL,
    synchronized_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_sync_artifact PRIMARY KEY (id),
    CONSTRAINT uk_sync_artifact_scope UNIQUE (tenant_id, knowledge_space_id, id),
    CONSTRAINT fk_sync_artifact_revision FOREIGN KEY (tenant_id, knowledge_space_id, data_source_id, source_revision)
        REFERENCES knowledge_data.source_revision (tenant_id, knowledge_space_id, data_source_id, revision),
    CONSTRAINT ck_sync_artifact_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_sync_artifact_record_count CHECK (record_count >= 0)
);

CREATE TABLE knowledge_data.quality_report (
    artifact_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    rule_set_version VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    warning_count BIGINT NOT NULL,
    blocking_count BIGINT NOT NULL,
    CONSTRAINT pk_quality_report PRIMARY KEY (artifact_id),
    CONSTRAINT fk_quality_report_artifact FOREIGN KEY (tenant_id, knowledge_space_id, artifact_id)
        REFERENCES knowledge_data.sync_artifact (tenant_id, knowledge_space_id, id),
    CONSTRAINT ck_quality_report_status CHECK (status IN ('PASS', 'WARN', 'BLOCK')),
    CONSTRAINT ck_quality_report_counts CHECK (
        warning_count >= 0 AND blocking_count >= 0
        AND (status <> 'PASS' OR (warning_count = 0 AND blocking_count = 0))
        AND (status <> 'WARN' OR (warning_count > 0 AND blocking_count = 0))
        AND (status <> 'BLOCK' OR blocking_count > 0)
    )
);
