CREATE TABLE IF NOT EXISTS platform_tenant (
    id BIGINT PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL UNIQUE,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS iam_user (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iam_user_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT uk_iam_user_tenant_username UNIQUE (tenant_id, username)
);

CREATE INDEX IF NOT EXISTS idx_iam_user_tenant ON iam_user (tenant_id);

CREATE TABLE IF NOT EXISTS iam_user_credential (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iam_user_credential_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT fk_iam_user_credential_user FOREIGN KEY (user_id) REFERENCES iam_user (id),
    CONSTRAINT uk_iam_user_credential_user UNIQUE (tenant_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_iam_user_credential_tenant ON iam_user_credential (tenant_id);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    detail VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_created ON audit_log (tenant_id, created_at DESC);
