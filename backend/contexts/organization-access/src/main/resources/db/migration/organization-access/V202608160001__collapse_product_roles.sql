-- 所属上下文：organization-access。
-- 目的：落实 ADR-0067，将平台与租户授权事实收敛为平台管理员、租户管理员和普通用户三种产品角色。
-- 影响：本地研发基线中的旧固定角色统一映射到新角色；成员权限修订递增，既有会话必须重新认证。
-- 回滚：不可安全回滚；旧角色语义已被产品决定替代，回退前必须重建本地研发数据库。

ALTER TABLE organization_access.tenant_membership
    DROP CONSTRAINT ck_oa_membership_role;

UPDATE organization_access.tenant_membership
SET membership_role = CASE membership_role
    WHEN 'TENANT_ADMINISTRATOR' THEN 'TENANT_ADMINISTRATOR'
    ELSE 'TENANT_USER'
END,
    permission_version = permission_version + 1,
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE organization_access.tenant_membership
    ADD CONSTRAINT ck_oa_membership_role
        CHECK (membership_role IN ('TENANT_ADMINISTRATOR', 'TENANT_USER'));

ALTER TABLE organization_access.tenant_membership_role_binding
    DROP CONSTRAINT ck_oa_tenant_membership_role_binding_role;

DELETE FROM organization_access.tenant_membership_role_binding;

INSERT INTO organization_access.tenant_membership_role_binding (
    tenant_id,
    membership_id,
    role_code,
    bound_at
)
SELECT
    tenant_id,
    membership_id,
    membership_role,
    CURRENT_TIMESTAMP
FROM organization_access.tenant_membership;

ALTER TABLE organization_access.tenant_membership_role_binding
    ADD CONSTRAINT ck_oa_tenant_membership_role_binding_role
        CHECK (role_code IN ('TENANT_ADMINISTRATOR', 'TENANT_USER'));

UPDATE organization_access.platform_role_binding duplicate
SET status = 'REVOKED',
    version = duplicate.version + 1,
    revoked_at = CURRENT_TIMESTAMP
WHERE duplicate.status = 'ACTIVE'
  AND EXISTS (
      SELECT 1
      FROM organization_access.platform_role_binding retained
      WHERE retained.subject_id = duplicate.subject_id
        AND retained.status = 'ACTIVE'
        AND retained.binding_id < duplicate.binding_id
  );

ALTER TABLE organization_access.platform_role_binding
    DROP CONSTRAINT ck_oa_platform_role_binding_role;

UPDATE organization_access.platform_role_binding
SET role_code = 'PLATFORM_ADMINISTRATOR';

ALTER TABLE organization_access.platform_role_binding
    ADD CONSTRAINT ck_oa_platform_role_binding_role
        CHECK (role_code = 'PLATFORM_ADMINISTRATOR');
