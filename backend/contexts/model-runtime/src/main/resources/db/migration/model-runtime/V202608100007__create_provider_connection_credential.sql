-- model-runtime：为租户厂商连接保存不可复用的加密凭据材料；不回填或暴露明文，可随 Git 回滚代码但不能回滚已轮换的外部秘密。
CREATE TABLE model_runtime.provider_connection_credential (
    tenant_id BIGINT NOT NULL,
    provider_connection_id BIGINT NOT NULL,
    algorithm VARCHAR(64) NOT NULL,
    master_key_version VARCHAR(120) NOT NULL,
    data_key_nonce BYTEA NOT NULL,
    encrypted_data_key BYTEA NOT NULL,
    ciphertext_nonce BYTEA NOT NULL,
    ciphertext BYTEA NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    rotated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_provider_connection_credential PRIMARY KEY (tenant_id, provider_connection_id),
    CONSTRAINT uq_provider_connection_credential_connection UNIQUE (provider_connection_id),
    CONSTRAINT fk_provider_connection_credential_connection
        FOREIGN KEY (tenant_id, provider_connection_id)
        REFERENCES model_runtime.provider_connection (tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT ck_provider_connection_credential_algorithm
        CHECK (algorithm IN ('AES/GCM/NoPadding')),
    CONSTRAINT ck_provider_connection_credential_material
        CHECK (
            octet_length(data_key_nonce) > 0
            AND octet_length(encrypted_data_key) > 0
            AND octet_length(ciphertext_nonce) > 0
            AND octet_length(ciphertext) > 0
        )
);
