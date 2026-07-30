-- 所属上下文：知识数据。目的：让索引快照保持同步产物冻结的规范化文本媒体类型，不再回退为来源修订媒体事实。
-- 数据影响：仅收敛受治理同步产物快照的媒体类型约束；前向迁移，不自动转换历史不一致快照。
ALTER TABLE knowledge_data.index_version_source
    DROP CONSTRAINT ck_index_version_source_typed_media_type,
    ADD CONSTRAINT ck_index_version_source_typed_media_type
        CHECK (
            (source_type = 'LEGACY_TEXT_CONTENT' AND media_type = 'text/plain; charset=UTF-8')
            OR (source_type = 'GOVERNED_SYNC_ARTIFACT' AND media_type = 'text/plain; charset=UTF-8')
        );
