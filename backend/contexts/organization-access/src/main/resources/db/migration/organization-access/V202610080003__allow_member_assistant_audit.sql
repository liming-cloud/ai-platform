-- organization-access：成员助手页与策略发布共用原授权关系，登记独立助手操作的非秘密审计。
-- 不修改既有关系或授权版本；回滚前必须确认没有 ASSISTANTS 审计，不能删除历史记录以回退。
ALTER TABLE organization_access.member_account_operation DROP CONSTRAINT member_account_operation_operation_check;
ALTER TABLE organization_access.member_account_operation ADD CONSTRAINT member_account_operation_operation_check
    CHECK (operation IN ('CREATE', 'RESET', 'DISABLE', 'ENABLE', 'FEATURES', 'ASSISTANTS'));
