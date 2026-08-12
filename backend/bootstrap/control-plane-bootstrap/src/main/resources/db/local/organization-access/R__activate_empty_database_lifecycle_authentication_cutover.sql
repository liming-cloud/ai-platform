-- 所属上下文：organization-access，本地研发 profile。
-- 目的：空数据库没有历史租户可回填时，启用 ADR-0046 的生命周期认证单一事实来源。
-- 影响：只写入不含主体、成员、凭据或会话的 singleton 切换事实；已有任一租户时不写入。
-- 回滚：不可自动回滚；仅允许通过重建可丢弃的本地数据库恢复，不得用于共享或生产环境。
INSERT INTO organization_access.tenant_lifecycle_authentication_cutover (
    singleton_key,
    activated_at
)
SELECT
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM organization_access.tenant
)
ON CONFLICT (singleton_key) DO NOTHING;
