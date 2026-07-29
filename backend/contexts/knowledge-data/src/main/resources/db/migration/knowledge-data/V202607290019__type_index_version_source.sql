-- 所属上下文：知识数据。目的：让索引快照同时冻结历史文本内容和已发布同步产物。
-- 影响：保留既有文本快照；取消只指向文本内容的外键，新增类型化来源事实。该迁移不提供降级回滚。
ALTER TABLE knowledge_data.index_version_source
    DROP CONSTRAINT fk_index_version_source_content,
    DROP CONSTRAINT uk_index_version_source_version_data_source,
    ALTER COLUMN content_version DROP NOT NULL,
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'LEGACY_TEXT_CONTENT',
    ADD COLUMN source_revision BIGINT,
    ADD COLUMN sync_artifact_id BIGINT,
    ADD COLUMN quality_status VARCHAR(16),
    ADD COLUMN quality_rule_set_version VARCHAR(128),
    ADD COLUMN published_at TIMESTAMPTZ;

UPDATE knowledge_data.index_version_source
SET source_revision = content_version
WHERE source_type = 'LEGACY_TEXT_CONTENT';

ALTER TABLE knowledge_data.index_version_source
    ALTER COLUMN source_revision SET NOT NULL,
    ADD CONSTRAINT uk_index_version_source_typed_revision
        UNIQUE (
            tenant_id,
            knowledge_space_id,
            index_version_id,
            source_type,
            data_source_id,
            source_revision
        ),
    ADD CONSTRAINT ck_index_version_source_type
        CHECK (source_type IN ('LEGACY_TEXT_CONTENT', 'GOVERNED_SYNC_ARTIFACT')),
    ADD CONSTRAINT ck_index_version_source_typed_facts
        CHECK (
            (
                source_type = 'LEGACY_TEXT_CONTENT'
                AND content_version IS NOT NULL
                AND sync_artifact_id IS NULL
                AND quality_status IS NULL
                AND quality_rule_set_version IS NULL
                AND published_at IS NULL
            )
            OR (
                source_type = 'GOVERNED_SYNC_ARTIFACT'
                AND content_version IS NULL
                AND sync_artifact_id IS NOT NULL
                AND quality_status IN ('PASS', 'WARN')
                AND quality_rule_set_version IS NOT NULL
                AND published_at IS NOT NULL
            )
        ),
    ADD CONSTRAINT fk_index_version_source_artifact
        FOREIGN KEY (sync_artifact_id)
        REFERENCES knowledge_data.sync_artifact (id);
