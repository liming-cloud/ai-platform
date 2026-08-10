-- 所属上下文：organization-access。
-- 目的：持久化租户拥有的版本化自定义角色及其已发布权限集合，供后续成员角色绑定引用。
-- 影响：新增角色和权限表，不修改既有成员直授权限或内置角色绑定。
-- 回滚：不可自动回滚；删除角色会使后续版本无法恢复角色目录，必须先确认不存在引用绑定。

CREATE TABLE organization_access.tenant_custom_role (
    custom_role_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES organization_access.tenant (tenant_id) ON DELETE CASCADE,
    role_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    disabled_at TIMESTAMPTZ,
    CONSTRAINT ck_oa_tenant_custom_role_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_oa_tenant_custom_role_state
        CHECK ((status = 'ACTIVE' AND disabled_at IS NULL)
            OR (status = 'DISABLED' AND disabled_at IS NOT NULL)),
    CONSTRAINT ck_oa_tenant_custom_role_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_oa_tenant_custom_role_active_name
    ON organization_access.tenant_custom_role (tenant_id, role_name)
    WHERE status = 'ACTIVE';

CREATE TABLE organization_access.tenant_custom_role_permission (
    custom_role_id BIGINT NOT NULL
        REFERENCES organization_access.tenant_custom_role (custom_role_id) ON DELETE CASCADE,
    permission_code VARCHAR(128) NOT NULL,
    CONSTRAINT pk_oa_tenant_custom_role_permission PRIMARY KEY (custom_role_id, permission_code),
    CONSTRAINT ck_oa_tenant_custom_role_permission_code
        CHECK (permission_code IN (
            'access:manage',
            'knowledge:read',
            'knowledge:write',
            'model:read',
            'model:write',
            'assistant:read',
            'assistant:write',
            'assistant:query',
            'usage:read',
            'audit:read'
        ))
);
