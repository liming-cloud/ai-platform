-- 所属上下文：organization-access。
-- 目的：保存与租户成员资格分离的平台租户治理授权事实。
-- 影响：平台会话只能由已启用全局主体和有效授权共同恢复；不包含凭据、租户正文或成员详情。
-- 回滚：可先删除平台会话并删除本迁移创建的两张授权事实表，不影响主体或租户成员资格。

CREATE TABLE organization_access.platform_access_grant (
    subject_id BIGINT PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    permission_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_oa_platform_access_subject
        FOREIGN KEY (subject_id)
        REFERENCES organization_access.subject_identity (subject_id),
    CONSTRAINT ck_oa_platform_access_status
        CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_oa_platform_access_permission_version
        CHECK (permission_version >= 0),
    CONSTRAINT ck_oa_platform_access_version
        CHECK (version >= 0)
);

CREATE TABLE organization_access.platform_access_permission (
    subject_id BIGINT NOT NULL,
    permission_code VARCHAR(96) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_oa_platform_access_permission
        PRIMARY KEY (subject_id, permission_code),
    CONSTRAINT fk_oa_platform_access_permission_grant
        FOREIGN KEY (subject_id)
        REFERENCES organization_access.platform_access_grant (subject_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_oa_platform_access_permission_code
        CHECK (permission_code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$')
);
