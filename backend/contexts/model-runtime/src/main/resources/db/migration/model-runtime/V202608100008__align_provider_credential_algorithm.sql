-- model-runtime：对齐实际 AES-GCM 密文算法标识，修复已建表约束拒绝有效密文；可迁移到新约束，不能回滚已轮换的外部秘密。
ALTER TABLE model_runtime.provider_connection_credential
    DROP CONSTRAINT ck_provider_connection_credential_algorithm;

ALTER TABLE model_runtime.provider_connection_credential
    ADD CONSTRAINT ck_provider_connection_credential_algorithm
        CHECK (algorithm IN ('AES-256-GCM'));
