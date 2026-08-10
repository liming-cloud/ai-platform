-- 所属上下文：organization-access。
-- 目的：扩展已发布的租户内置角色目录，使角色权限由领域枚举投影而非认证查询中的角色名称分支决定。
-- 影响：既有 `TENANT_ACCESS_ADMINISTRATOR` 绑定保持有效；允许写入知识、模型、助手、审计和问答角色。
-- 回滚：不可自动回滚；若回退应用版本，必须先确认新增角色绑定已被移除或由兼容版本读取。

ALTER TABLE organization_access.tenant_membership_role_binding
    DROP CONSTRAINT ck_oa_tenant_membership_role_binding_role;

ALTER TABLE organization_access.tenant_membership_role_binding
    ADD CONSTRAINT ck_oa_tenant_membership_role_binding_role
        CHECK (role_code IN (
            'TENANT_ACCESS_ADMINISTRATOR',
            'TENANT_KNOWLEDGE_MANAGER',
            'TENANT_MODEL_MANAGER',
            'TENANT_ASSISTANT_OWNER',
            'TENANT_AUDITOR',
            'TENANT_QUERY_USER'
        ));
