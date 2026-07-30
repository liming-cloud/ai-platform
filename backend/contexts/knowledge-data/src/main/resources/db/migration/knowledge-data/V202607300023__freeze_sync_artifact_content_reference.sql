-- 所属上下文：知识数据。目的：让同步产物冻结可由索引读取的私有规范化文本引用；不保存平台对象存储位置。
-- 数据影响：为同步产物增加私有文本对象完整性事实；历史产物保留空引用且不得进入 M3 索引，新产物必须完整冻结全部引用事实。仅前向迁移。
ALTER TABLE knowledge_data.sync_artifact
    ADD COLUMN content_version INTEGER,
    ADD COLUMN content_object_key VARCHAR(1024),
    ADD COLUMN content_byte_size BIGINT,
    ADD COLUMN content_media_type VARCHAR(120),
    ADD COLUMN content_created_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_sync_artifact_content_reference
        CHECK (
            (content_version IS NULL
                AND content_object_key IS NULL
                AND content_byte_size IS NULL
                AND content_media_type IS NULL
                AND content_created_at IS NULL)
            OR
            (content_version > 0
                AND content_object_key IS NOT NULL
                AND content_byte_size > 0
                AND content_media_type = 'text/plain; charset=UTF-8'
                AND content_created_at IS NOT NULL)
        );
