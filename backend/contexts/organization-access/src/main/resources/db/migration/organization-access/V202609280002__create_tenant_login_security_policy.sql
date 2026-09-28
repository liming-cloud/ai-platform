-- 所属上下文：organization-access。依据 ADR-0086，为每租户登录设置与失败观察建立独立事实。
-- 现有租户显式回填 8 小时、5 次、15 分钟；新租户必须在开户事务中写入策略。
-- 删除策略会丢失已保存的安全选择与锁定状态，不能作为无损自动回滚。
CREATE TABLE organization_access.tenant_login_security_policy (
    tenant_id BIGINT PRIMARY KEY REFERENCES organization_access.tenant (tenant_id) ON DELETE CASCADE,
    session_hours INTEGER NOT NULL CHECK (session_hours BETWEEN 1 AND 24),
    failed_attempts INTEGER NOT NULL CHECK (failed_attempts BETWEEN 3 AND 10),
    lock_minutes INTEGER NOT NULL CHECK (lock_minutes BETWEEN 5 AND 60),
    version BIGINT NOT NULL CHECK (version >= 0)
);

INSERT INTO organization_access.tenant_login_security_policy
    (tenant_id, session_hours, failed_attempts, lock_minutes, version)
SELECT tenant_id, 8, 5, 15, 0 FROM organization_access.tenant;

CREATE TABLE organization_access.tenant_login_attempt (
    tenant_id BIGINT NOT NULL REFERENCES organization_access.tenant (tenant_id) ON DELETE CASCADE,
    subject_id BIGINT NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    failures INTEGER NOT NULL CHECK (failures >= 0),
    locked_until TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, subject_id)
);
