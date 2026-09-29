-- 所属上下文：organization-access。记录平台管理员账户操作的非秘密成功事实，与身份写入同事务提交。
-- 本表不存储密码、摘要和会话；上线后审计事实不可通过回滚迁移直接删除。
CREATE TABLE organization_access.platform_account_operation_audit (
    operation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operator_subject_id BIGINT NOT NULL REFERENCES organization_access.subject_identity(subject_id),
    target_subject_id BIGINT NOT NULL REFERENCES organization_access.subject_identity(subject_id),
    operation VARCHAR(32) NOT NULL,
    result VARCHAR(16) NOT NULL,
    target_version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oa_platform_account_operation
        CHECK (operation IN ('CREATE', 'RESET_PASSWORD', 'DISABLE', 'ENABLE')),
    CONSTRAINT ck_oa_platform_account_operation_result CHECK (result = 'SUCCEEDED'),
    CONSTRAINT ck_oa_platform_account_operation_version CHECK (target_version >= 0)
);

CREATE INDEX ix_oa_platform_account_operation_target
    ON organization_access.platform_account_operation_audit(target_subject_id, occurred_at DESC);
