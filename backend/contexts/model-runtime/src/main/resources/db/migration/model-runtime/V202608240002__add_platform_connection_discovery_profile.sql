-- 所属上下文：model-runtime。目的：为平台连接冻结权威模型发现协议，避免按 Endpoint 或模型名推断。
-- 影响：历史连接按既有协议迁移为唯一兼容配置；回滚会丢失显式发现选择。
ALTER TABLE model_runtime.platform_provider_connection
    ADD COLUMN discovery_profile VARCHAR(48);

UPDATE model_runtime.platform_provider_connection
SET discovery_profile = CASE provider_kind
    WHEN 'DETERMINISTIC' THEN 'DETERMINISTIC_CATALOG'
    WHEN 'OPENAI_COMPATIBLE' THEN 'OPENAI_MODELS_API'
    ELSE 'NONE'
END;

ALTER TABLE model_runtime.platform_provider_connection
    ALTER COLUMN discovery_profile SET DEFAULT 'NONE',
    ALTER COLUMN discovery_profile SET NOT NULL,
    ADD CONSTRAINT ck_platform_provider_connection_discovery_profile CHECK (
        discovery_profile IN (
            'DETERMINISTIC_CATALOG',
            'OPENAI_MODELS_API',
            'VOLCENGINE_ARK_CONTROL_PLANE',
            'NONE'
        )
    );
