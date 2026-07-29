-- 所属上下文：知识数据。目的：按来源类型校验冻结媒体类型，兼容历史文本与 FILE 来源事实。
-- 影响：只调整 V202607290019 后新增的类型化来源校验；不修改任何既有快照数据，无法自动降级回滚。
ALTER TABLE knowledge_data.index_version_source
    DROP CONSTRAINT ck_index_version_source_media_type,
    ADD CONSTRAINT ck_index_version_source_typed_media_type
        CHECK (
            (source_type = 'LEGACY_TEXT_CONTENT' AND media_type = 'text/plain; charset=UTF-8')
            OR (source_type = 'GOVERNED_SYNC_ARTIFACT' AND media_type = 'text/plain; charset=utf-8')
        );
