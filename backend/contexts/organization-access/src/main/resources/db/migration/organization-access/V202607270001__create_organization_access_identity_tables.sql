CREATE SCHEMA IF NOT EXISTS organization_access;

CREATE TABLE organization_access.tenant (
    tenant_id UUID PRIMARY KEY,
    tenant_code VARCHAR(63) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_tenant_code UNIQUE (tenant_code),
    CONSTRAINT ck_oa_tenant_code
        CHECK (tenant_code ~ '^[a-z][a-z0-9-]{2,62}$'),
    CONSTRAINT ck_oa_tenant_status
        CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_oa_tenant_version
        CHECK (version >= 0)
);

CREATE TABLE organization_access.membership_credential (
    membership_id BIGINT PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_id BIGINT NOT NULL,
    login_name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    status VARCHAR(16) NOT NULL,
    permission_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_oa_membership_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES organization_access.tenant (tenant_id),
    CONSTRAINT uk_oa_membership_tenant_login
        UNIQUE (tenant_id, login_name),
    CONSTRAINT uk_oa_membership_tenant_id
        UNIQUE (tenant_id, membership_id),
    CONSTRAINT ck_oa_membership_login_name
        CHECK (login_name ~ '^[a-z0-9][a-z0-9._-]{2,63}$'),
    CONSTRAINT ck_oa_membership_password_hash
        CHECK (char_length(password_hash) BETWEEN 8 AND 256),
    CONSTRAINT ck_oa_membership_status
        CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_oa_membership_permission_version
        CHECK (permission_version >= 0),
    CONSTRAINT ck_oa_membership_version
        CHECK (version >= 0)
);

CREATE INDEX ix_oa_membership_tenant_subject
    ON organization_access.membership_credential (tenant_id, subject_id);

CREATE TABLE organization_access.membership_permission (
    tenant_id UUID NOT NULL,
    membership_id BIGINT NOT NULL,
    permission_code VARCHAR(96) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oa_membership_permission
        PRIMARY KEY (tenant_id, membership_id, permission_code),
    CONSTRAINT fk_oa_permission_membership
        FOREIGN KEY (tenant_id, membership_id)
        REFERENCES organization_access.membership_credential (tenant_id, membership_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_oa_permission_code
        CHECK (permission_code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$')
);
