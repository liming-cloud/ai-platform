-- 所属上下文：organization-access。
-- 目的：持久化成员与同租户自定义角色的不可变绑定事实，保证成员与角色的租户归属由数据库闭合校验。
-- 影响：新增自定义角色绑定表和角色租户复合唯一约束，不修改既有成员直授权限或内置角色绑定。
-- 回滚：不可自动回滚；删除绑定会丢失授权审计事实，删除复合约束前必须先删除依赖外键。

ALTER TABLE organization_access.tenant_custom_role
    ADD CONSTRAINT uk_oa_tenant_custom_role_tenant_id UNIQUE (tenant_id, custom_role_id);

CREATE TABLE organization_access.tenant_custom_role_binding (
    tenant_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    custom_role_id BIGINT NOT NULL,
    bound_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_oa_tenant_custom_role_binding
        PRIMARY KEY (tenant_id, membership_id, custom_role_id),
    CONSTRAINT fk_oa_tenant_custom_role_binding_membership
        FOREIGN KEY (tenant_id, membership_id)
        REFERENCES organization_access.tenant_membership (tenant_id, membership_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_oa_tenant_custom_role_binding_role
        FOREIGN KEY (tenant_id, custom_role_id)
        REFERENCES organization_access.tenant_custom_role (tenant_id, custom_role_id)
        ON DELETE CASCADE
);
