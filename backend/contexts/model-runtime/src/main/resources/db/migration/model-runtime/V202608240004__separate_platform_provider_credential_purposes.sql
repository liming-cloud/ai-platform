-- 所属上下文：model-runtime/platform-supply。
-- 目的：将推理 API Key 与方舟控制面 AK/SK 分槽密文保存，防止轮换或写入时互相覆盖。
-- 影响：既有凭据标记为 `INFERENCE_API_KEY`；每个连接最多保存三种受控用途的凭据。
-- 回滚：不可安全回滚；回滚会丢失控制面 AK/SK，必须先停止方舟自动发现并导出受管凭据。

ALTER TABLE model_runtime.platform_provider_connection_credential
    ADD COLUMN credential_purpose VARCHAR(64) NOT NULL DEFAULT 'INFERENCE_API_KEY';

ALTER TABLE model_runtime.platform_provider_connection_credential
    DROP CONSTRAINT platform_provider_connection_credential_pkey;

ALTER TABLE model_runtime.platform_provider_connection_credential
    ADD CONSTRAINT pk_platform_provider_connection_credential
        PRIMARY KEY (platform_provider_connection_id, credential_purpose),
    ADD CONSTRAINT ck_platform_provider_connection_credential_purpose
        CHECK (credential_purpose IN (
            'INFERENCE_API_KEY',
            'DISCOVERY_ACCESS_KEY_ID',
            'DISCOVERY_SECRET_ACCESS_KEY'
        ));
