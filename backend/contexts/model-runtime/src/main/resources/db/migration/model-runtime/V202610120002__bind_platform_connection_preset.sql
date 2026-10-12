-- 所属上下文：model-runtime/platform-supply。
-- 目的：连接修订冻结厂商预设、版本和能力意图，防止前端预填被当成后端验证。
-- 影响：新增非秘密来源字段；历史记录保留空值，不按旧地址推断来源或补造能力事实。
-- 回滚：不可无损回滚；删除字段将丢失新连接的来源绑定，须先停止相关写入。
ALTER TABLE model_runtime.platform_provider_connection
    ADD COLUMN provider_preset_id VARCHAR(32),
    ADD COLUMN provider_preset_version VARCHAR(10),
    ADD COLUMN capability_intent VARCHAR(16),
    ADD COLUMN workspace_id VARCHAR(63),
    ADD CONSTRAINT ck_platform_connection_preset_completeness CHECK (
        (provider_preset_id IS NULL AND provider_preset_version IS NULL AND capability_intent IS NULL AND workspace_id IS NULL)
        OR (provider_preset_id IS NOT NULL AND provider_preset_version IS NOT NULL AND capability_intent IS NOT NULL)
    ),
    ADD CONSTRAINT ck_platform_connection_capability_intent CHECK (capability_intent IN ('CHAT', 'EMBEDDING', 'RERANK')),
    ADD CONSTRAINT ck_platform_connection_workspace CHECK (workspace_id ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$');
