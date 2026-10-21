-- 所属上下文：model-runtime。目的：按 ADR-0101 增加显式重排发现协议与不可变映射来源。
-- 影响：仅新增选择及可空来源字段，旧连接和候选不补写、不升级；回滚会丢失新来源证明，需先停止新来源写入。
ALTER TABLE model_runtime.platform_provider_connection
    DROP CONSTRAINT ck_platform_provider_connection_discovery_profile;
ALTER TABLE model_runtime.platform_provider_connection
    ADD CONSTRAINT ck_platform_provider_connection_discovery_profile CHECK (
        discovery_profile IN ('DETERMINISTIC_CATALOG', 'OPENAI_MODELS_API', 'VOLCENGINE_ARK_CONTROL_PLANE', 'SILICONFLOW_RERANK_MODELS_API', 'NONE'));
ALTER TABLE model_runtime.platform_discovered_model
    ADD COLUMN source_profile VARCHAR(48),
    ADD COLUMN list_source VARCHAR(200),
    ADD COLUMN mapping_version VARCHAR(80),
    ADD COLUMN mapping_digest VARCHAR(64),
    ADD CONSTRAINT ck_platform_candidate_source_complete CHECK (
        (source_profile IS NULL AND list_source IS NULL AND mapping_version IS NULL AND mapping_digest IS NULL)
        OR (source_profile IS NOT NULL AND list_source IS NOT NULL AND mapping_version IS NOT NULL AND mapping_digest IS NOT NULL
            AND source_profile = 'SILICONFLOW_RERANK_MODELS_API'
            AND list_source = 'https://api.siliconflow.cn/v1/models?sub_type=reranker'
            AND mapping_version ~ '^siliconflow-rerank-[0-9]{4}-[0-9]{2}-[0-9]{2}$'
            AND mapping_digest ~ '^[0-9a-f]{64}$'));
