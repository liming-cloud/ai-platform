-- 所属上下文：知识数据。目的：冻结 M3 STRUCTURED_DATASET 私有 UTF-8 CSV、字段映射和受控行过滤；不引入外部连接器、SQL 或凭据。
-- 数据影响：扩展来源修订并新增冻结字段映射子表。不可回滚，生产环境仅允许前向迁移。
ALTER TABLE knowledge_data.source_revision
    DROP CONSTRAINT ck_source_revision_typed_object_facts,
    ADD COLUMN dataset_mapping_version VARCHAR(64),
    ADD COLUMN dataset_content_field VARCHAR(128),
    ADD COLUMN dataset_title_field VARCHAR(128),
    ADD COLUMN dataset_row_filter VARCHAR(32),
    ADD CONSTRAINT ck_source_revision_typed_facts CHECK (
        (source_type = 'FILE'
            AND object_key IS NOT NULL
            AND content_digest ~ '^[0-9a-f]{64}$'
            AND byte_size > 0
            AND media_type = 'text/plain; charset=utf-8'
            AND object_range_id IS NULL
            AND object_selection_revision IS NULL
            AND object_collection_digest IS NULL
            AND dataset_mapping_version IS NULL
            AND dataset_content_field IS NULL
            AND dataset_title_field IS NULL
            AND dataset_row_filter IS NULL)
        OR
        (source_type = 'OBJECT_STORAGE'
            AND object_key IS NULL
            AND content_digest IS NULL
            AND byte_size IS NULL
            AND media_type IS NULL
            AND object_range_id ~ '^rng_[A-Za-z0-9]{16,128}$'
            AND object_selection_revision > 0
            AND object_collection_digest ~ '^[0-9a-f]{64}$'
            AND dataset_mapping_version IS NULL
            AND dataset_content_field IS NULL
            AND dataset_title_field IS NULL
            AND dataset_row_filter IS NULL)
        OR
        (source_type = 'STRUCTURED_DATASET'
            AND object_key IS NOT NULL
            AND content_digest ~ '^[0-9a-f]{64}$'
            AND byte_size > 0
            AND media_type = 'text/csv; charset=utf-8'
            AND object_range_id IS NULL
            AND object_selection_revision IS NULL
            AND object_collection_digest IS NULL
            AND dataset_mapping_version ~ '^[a-z][a-z0-9-]{2,63}$'
            AND dataset_content_field IS NOT NULL
            AND dataset_row_filter IN ('ALL_ROWS', 'NON_BLANK_CONTENT'))
        OR
        (source_type NOT IN ('FILE', 'OBJECT_STORAGE', 'STRUCTURED_DATASET')
            AND object_key IS NULL
            AND content_digest IS NULL
            AND byte_size IS NULL
            AND media_type IS NULL
            AND object_range_id IS NULL
            AND object_selection_revision IS NULL
            AND object_collection_digest IS NULL
            AND dataset_mapping_version IS NULL
            AND dataset_content_field IS NULL
            AND dataset_title_field IS NULL
            AND dataset_row_filter IS NULL)
    );

CREATE TABLE knowledge_data.source_revision_dataset_field (
    tenant_id BIGINT NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    field_ordinal INTEGER NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    logical_type VARCHAR(16) NOT NULL,
    sensitivity_level VARCHAR(16) NOT NULL,
    CONSTRAINT pk_source_revision_dataset_field PRIMARY KEY (
        tenant_id, knowledge_space_id, data_source_id, revision, field_ordinal
    ),
    CONSTRAINT uk_source_revision_dataset_field_name UNIQUE (
        tenant_id, knowledge_space_id, data_source_id, revision, field_name
    ),
    CONSTRAINT fk_source_revision_dataset_field_revision FOREIGN KEY (
        tenant_id, knowledge_space_id, data_source_id, revision
    ) REFERENCES knowledge_data.source_revision (
        tenant_id, knowledge_space_id, data_source_id, revision
    ),
    CONSTRAINT ck_source_revision_dataset_field_ordinal CHECK (field_ordinal >= 0),
    CONSTRAINT ck_source_revision_dataset_field_type CHECK (
        logical_type IN ('TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATETIME')
    ),
    CONSTRAINT ck_source_revision_dataset_field_sensitivity CHECK (
        sensitivity_level IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
    )
);
