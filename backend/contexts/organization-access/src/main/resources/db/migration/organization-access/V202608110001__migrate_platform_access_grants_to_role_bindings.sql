-- 所属上下文：organization-access。
-- 目的：以固定平台角色绑定替代直接平台权限授予，并保留每个主体的认证权限修订。
-- 影响：认证只从活动角色绑定派生权限；历史租户治理直接授权迁移为 `PLATFORM_TENANT_GOVERNOR`，其余有效会话授权迁移为 `PLATFORM_OPERATOR`。
-- 回滚：不可安全回滚；回退代码必须仍能读取旧表，且新表只作为迁移后认证事实。

CREATE TABLE organization_access.platform_subject_authorization (
    subject_id BIGINT PRIMARY KEY,
    permission_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_oa_platform_subject_authorization_subject
        FOREIGN KEY (subject_id) REFERENCES organization_access.subject_identity (subject_id),
    CONSTRAINT ck_oa_platform_subject_authorization_permission_version CHECK (permission_version >= 0),
    CONSTRAINT ck_oa_platform_subject_authorization_version CHECK (version >= 0)
);

CREATE TABLE organization_access.platform_role_binding (
    binding_id BIGINT PRIMARY KEY,
    subject_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT fk_oa_platform_role_binding_subject
        FOREIGN KEY (subject_id) REFERENCES organization_access.subject_identity (subject_id),
    CONSTRAINT ck_oa_platform_role_binding_role
        CHECK (role_code IN ('PLATFORM_ACCOUNT_ADMINISTRATOR', 'PLATFORM_TENANT_GOVERNOR', 'PLATFORM_OPERATOR')),
    CONSTRAINT ck_oa_platform_role_binding_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_oa_platform_role_binding_revision CHECK (version >= 0),
    CONSTRAINT ck_oa_platform_role_binding_revoked_at
        CHECK ((status = 'ACTIVE' AND revoked_at IS NULL) OR (status = 'REVOKED' AND revoked_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_oa_platform_role_binding_active
    ON organization_access.platform_role_binding (subject_id, role_code)
    WHERE status = 'ACTIVE';

INSERT INTO organization_access.platform_subject_authorization (
    subject_id, permission_version, version, created_at, updated_at
)
SELECT subject_id, permission_version, version, created_at, updated_at
FROM organization_access.platform_access_grant
WHERE status = 'ENABLED'
ON CONFLICT (subject_id) DO NOTHING;

INSERT INTO organization_access.platform_role_binding (
    binding_id, subject_id, role_code, status, version, created_at
)
SELECT access_grant.subject_id,
       access_grant.subject_id,
       CASE WHEN EXISTS (
           SELECT 1 FROM organization_access.platform_access_permission permission
           WHERE permission.subject_id = access_grant.subject_id
             AND permission.permission_code = 'tenant-governance:write'
       ) THEN 'PLATFORM_TENANT_GOVERNOR' ELSE 'PLATFORM_OPERATOR' END,
       'ACTIVE', 0, access_grant.created_at
FROM organization_access.platform_access_grant access_grant
WHERE access_grant.status = 'ENABLED'
ON CONFLICT (binding_id) DO NOTHING;
