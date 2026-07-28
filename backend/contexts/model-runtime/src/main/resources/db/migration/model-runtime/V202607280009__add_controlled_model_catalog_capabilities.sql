-- 所属上下文：model-runtime。目的：为受控模型目录冻结经平台确认的运行时能力快照。
-- 数据影响：历史条目允许保留全空快照，但运行时会失效关闭；新条目必须填充与模型类型一致的快照。
-- 回滚限制：删除列会丢失已发布目录的能力事实，不能在存在依赖助手版本时直接回滚。

ALTER TABLE model_runtime.controlled_model_catalog_entry
    ADD COLUMN context_window INTEGER,
    ADD COLUMN output_limit INTEGER,
    ADD COLUMN embedding_dimension INTEGER,
    ADD CONSTRAINT ck_controlled_model_catalog_capabilities
        CHECK (
            (context_window IS NULL AND output_limit IS NULL AND embedding_dimension IS NULL)
            OR (
                model_type = 'CHAT'
                AND context_window > 0
                AND output_limit > 0
                AND output_limit <= context_window
                AND embedding_dimension IS NULL
            )
            OR (
                model_type = 'EMBEDDING'
                AND context_window > 0
                AND output_limit IS NULL
                AND embedding_dimension > 0
            )
        );
