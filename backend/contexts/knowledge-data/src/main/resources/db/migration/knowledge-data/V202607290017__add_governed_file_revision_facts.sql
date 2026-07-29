-- 所属上下文：知识数据。目的：补齐 M3 文件来源的当前修订和不可变受控对象事实；不改变 M1 文本来源。
-- 数据影响：已有受治理来源默认首个修订；新增文件对象字段。不可回滚，生产环境仅允许前向迁移。
ALTER TABLE knowledge_data.governed_data_source
    ADD COLUMN current_revision BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_governed_data_source_current_revision CHECK (current_revision > 0);

ALTER TABLE knowledge_data.source_revision
    ADD COLUMN object_key VARCHAR(1024),
    ADD COLUMN content_digest CHAR(64),
    ADD COLUMN byte_size BIGINT,
    ADD COLUMN media_type VARCHAR(128),
    ADD CONSTRAINT ck_source_revision_file_object CHECK (
        (source_type = 'FILE'
            AND object_key IS NOT NULL
            AND content_digest ~ '^[0-9a-f]{64}$'
            AND byte_size > 0
            AND media_type = 'text/plain; charset=utf-8')
        OR
        (source_type <> 'FILE'
            AND object_key IS NULL
            AND content_digest IS NULL
            AND byte_size IS NULL
            AND media_type IS NULL)
    );
