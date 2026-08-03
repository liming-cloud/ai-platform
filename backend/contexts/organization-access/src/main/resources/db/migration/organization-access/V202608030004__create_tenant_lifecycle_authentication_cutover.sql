-- 所属上下文：organization-access。
-- 目的：以不含主体、成员或凭据详情的单行事实，标识历史租户生命周期已完整回填并可切换认证状态源。
-- 影响：切换事实仅由历史回填原子提交写入；它不存在时保持旧租户状态认证语义。
-- 回滚：不可自动回滚；错误切换必须经受控恢复流程和完整一致性复核处理。

CREATE TABLE organization_access.tenant_lifecycle_authentication_cutover (
    singleton_key BOOLEAN PRIMARY KEY,
    activated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_oa_tenant_lifecycle_authentication_cutover_singleton
        CHECK (singleton_key IS TRUE)
);
