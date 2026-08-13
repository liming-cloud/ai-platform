-- 所属上下文：model-runtime。目的：将受控模型目录绑定到已发布平台部署的冻结来源。
-- 数据影响：既有本地草稿保留空来源，不能作为 ADR-0065 新目录的创建结果。
-- 回滚限制：移除来源列会丢失目录供应链审计事实。

ALTER TABLE model_runtime.controlled_model_catalog_entry
    ADD COLUMN source_platform_deployment_id BIGINT,
    ADD COLUMN source_platform_deployment_revision BIGINT,
    ADD CONSTRAINT ck_controlled_model_catalog_source_deployment
        CHECK ((source_platform_deployment_id IS NULL AND source_platform_deployment_revision IS NULL)
            OR (source_platform_deployment_id > 0 AND source_platform_deployment_revision >= 0));

CREATE INDEX idx_controlled_model_catalog_source_deployment
    ON model_runtime.controlled_model_catalog_entry (
        source_platform_deployment_id, source_platform_deployment_revision)
    WHERE source_platform_deployment_id IS NOT NULL;
