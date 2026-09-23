-- 所属上下文：organization-access。
-- 目的：同一全局身份至多保留一份租户管理员资格，停用资格仍占用所属租户。
-- 影响：若历史数据已有重复管理员资格则拒绝迁移，不自动删除或改写成员。
-- 回滚：索引可以删除；业务数据不需要回滚。

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM organization_access.tenant_membership
        WHERE membership_role = 'TENANT_ADMINISTRATOR'
        GROUP BY subject_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION '无法约束单租户管理员：已有主体持有多个管理员资格';
    END IF;
END
$$;

CREATE UNIQUE INDEX uk_oa_single_tenant_administrator_subject
    ON organization_access.tenant_membership (subject_id)
    WHERE membership_role = 'TENANT_ADMINISTRATOR';
