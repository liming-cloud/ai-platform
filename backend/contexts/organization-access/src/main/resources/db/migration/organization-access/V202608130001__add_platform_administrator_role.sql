-- 所属上下文：organization-access。
-- 目的：发布 ADR-0065 的 `PLATFORM_ADMINISTRATOR` 固定角色，使其可以作为平台角色绑定持久化。
-- 影响：既有角色绑定和权限修订保持不变；仅允许新增平台管理员角色代码。
-- 回滚：不可安全回滚；已有平台管理员绑定时，回退代码必须先撤销该角色并确保会话族已失效。

ALTER TABLE organization_access.platform_role_binding
    DROP CONSTRAINT ck_oa_platform_role_binding_role;

ALTER TABLE organization_access.platform_role_binding
    ADD CONSTRAINT ck_oa_platform_role_binding_role
        CHECK (role_code IN (
            'PLATFORM_ACCOUNT_ADMINISTRATOR',
            'PLATFORM_TENANT_GOVERNOR',
            'PLATFORM_ADMINISTRATOR',
            'PLATFORM_OPERATOR'
        ));
