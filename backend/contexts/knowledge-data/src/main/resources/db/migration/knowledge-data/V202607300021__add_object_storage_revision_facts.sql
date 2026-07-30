-- 所属上下文：知识数据。目的：冻结 M3 OBJECT_STORAGE 的平台范围选择与实际对象集合；不引入外部连接器或平台管理接口。
-- 数据影响：扩展来源修订并新增冻结对象子表。不可回滚，生产环境仅允许前向迁移。
ALTER TABLE knowledge_data.source_revision
    DROP CONSTRAINT ck_source_revision_file_object,
    ADD COLUMN object_range_id VARCHAR(132),
    ADD COLUMN object_selection_revision BIGINT,
    ADD COLUMN object_collection_digest CHAR(64),
    ADD CONSTRAINT ck_source_revision_typed_object_facts CHECK (
        (source_type = 'FILE'
            AND object_key IS NOT NULL
            AND content_digest ~ '^[0-9a-f]{64}$'
            AND byte_size > 0
            AND media_type = 'text/plain; charset=utf-8'
            AND object_range_id IS NULL
            AND object_selection_revision IS NULL
            AND object_collection_digest IS NULL)
        OR
        (source_type = 'OBJECT_STORAGE'
            AND object_key IS NULL
            AND content_digest IS NULL
            AND byte_size IS NULL
            AND media_type IS NULL
            AND object_range_id ~ '^rng_[A-Za-z0-9]{16,128}$'
            AND object_selection_revision > 0
            AND object_collection_digest ~ '^[0-9a-f]{64}$')
        OR
        (source_type NOT IN ('FILE', 'OBJECT_STORAGE')
            AND object_key IS NULL
            AND content_digest IS NULL
            AND byte_size IS NULL
            AND media_type IS NULL
            AND object_range_id IS NULL
            AND object_selection_revision IS NULL
            AND object_collection_digest IS NULL)
    );

CREATE TABLE knowledge_data.source_revision_object (
    tenant_id BIGINT NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    object_ordinal INTEGER NOT NULL,
    platform_object_id VARCHAR(132) NOT NULL,
    content_digest CHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    fresh_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_source_revision_object PRIMARY KEY (
        tenant_id, knowledge_space_id, data_source_id, revision, object_ordinal
    ),
    CONSTRAINT uk_source_revision_object_id UNIQUE (
        tenant_id, knowledge_space_id, data_source_id, revision, platform_object_id
    ),
    CONSTRAINT fk_source_revision_object_revision FOREIGN KEY (
        tenant_id, knowledge_space_id, data_source_id, revision
    ) REFERENCES knowledge_data.source_revision (
        tenant_id, knowledge_space_id, data_source_id, revision
    ),
    CONSTRAINT ck_source_revision_object_ordinal CHECK (object_ordinal >= 0),
    CONSTRAINT ck_source_revision_object_id CHECK (platform_object_id ~ '^obj_[A-Za-z0-9]{16,128}$'),
    CONSTRAINT ck_source_revision_object_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_source_revision_object_size CHECK (byte_size > 0)
);
