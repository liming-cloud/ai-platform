-- 所属上下文：model-runtime。目的：按 ADR-0070 将候选显示名称与供应商调用编码分离。
-- 影响：仅新增可空列，后续发现冻结名称；不回填、不重发现、不修改既有模型或发布事实。
-- 回滚限制：旧源码不读取该列；删除此列会丢失新增候选的名称，须先停止相关写入并保留证据。
ALTER TABLE model_runtime.platform_discovered_model
    ADD COLUMN display_name VARCHAR(120) NULL,
    ADD CONSTRAINT ck_platform_candidate_display_name CHECK (
        display_name IS NULL OR (length(btrim(display_name)) > 0 AND display_name = btrim(display_name))
    );
