-- 所属上下文：organization-access。
-- 目的：将租户内置角色绑定与旧的成员直授权限分离，保证权限版本可随角色变更撤销会话。
-- 影响：为历史租户管理员补写访问管理角色；新角色绑定只写入本表，不再扩写 `membership_permission`。
-- 回滚：不可自动回滚；删除绑定会收回已授予的访问管理权限，必须先人工确认受影响成员。

CREATE TABLE organization_access.tenant_membership_role_binding (
    tenant_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    bound_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_oa_tenant_membership_role_binding
        PRIMARY KEY (tenant_id, membership_id, role_code),
    CONSTRAINT fk_oa_tenant_membership_role_binding_membership
        FOREIGN KEY (tenant_id, membership_id)
        REFERENCES organization_access.tenant_membership (tenant_id, membership_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_oa_tenant_membership_role_binding_role
        CHECK (role_code IN ('TENANT_ACCESS_ADMINISTRATOR'))
);

INSERT INTO organization_access.tenant_membership_role_binding (
    tenant_id,
    membership_id,
    role_code,
    bound_at
)
SELECT
    tenant_id,
    membership_id,
    'TENANT_ACCESS_ADMINISTRATOR',
    created_at
FROM organization_access.tenant_membership
WHERE membership_role = 'TENANT_ADMINISTRATOR'
ON CONFLICT DO NOTHING;
