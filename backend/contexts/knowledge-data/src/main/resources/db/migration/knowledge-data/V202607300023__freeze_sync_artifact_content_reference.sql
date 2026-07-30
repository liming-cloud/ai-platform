-- 所属上下文：知识数据。目的：让同步产物冻结可由索引读取的私有规范化文本引用；不保存平台对象存储位置。
-- 数据影响：为同步产物增加私有文本对象完整性事实；仅前向迁移，旧产物不具备该引用时不得进入 M3 索引。
ALTER TABLE knowledge_data.sync_artifact
    ADD COLUMN content_version INTEGER NOT NULL,
    ADD COLUMN content_object_key VARCHAR(1024) NOT NULL,
    ADD COLUMN content_byte_size BIGINT NOT NULL,
    ADD COLUMN content_media_type VARCHAR(120) NOT NULL,
    ADD COLUMN content_created_at TIMESTAMPTZ NOT NULL,
    ADD CONSTRAINT ck_sync_artifact_content_version CHECK (content_version > 0),
    ADD CONSTRAINT ck_sync_artifact_content_byte_size CHECK (content_byte_size > 0),
    ADD CONSTRAINT ck_sync_artifact_content_media_type CHECK (content_media_type = 'text/plain; charset=UTF-8');
