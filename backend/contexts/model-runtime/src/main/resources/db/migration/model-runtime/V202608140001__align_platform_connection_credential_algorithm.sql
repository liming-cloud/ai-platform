-- 所属上下文：model-runtime。
-- 目的：使平台范围厂商连接凭据的持久化算法约束与统一 AES-GCM 密钥封装器一致。
-- 影响：既有不可能写入的旧算法值不受影响；新建和轮换的平台连接凭据可保存 `AES-256-GCM` 密文。
-- 回滚：不可安全回滚；若回退应用版本，必须先确认其不会写入平台连接凭据。

ALTER TABLE model_runtime.platform_provider_connection_credential
    DROP CONSTRAINT ck_platform_provider_connection_credential_algorithm;

ALTER TABLE model_runtime.platform_provider_connection_credential
    ADD CONSTRAINT ck_platform_provider_connection_credential_algorithm
        CHECK (algorithm = 'AES-256-GCM');
