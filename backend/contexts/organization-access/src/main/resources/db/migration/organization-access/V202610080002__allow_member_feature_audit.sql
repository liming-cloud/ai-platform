-- organization-access：为独立功能保存增加无秘密审计类型；不改写既有账户操作或配置。
-- 回滚前须确认不存在 FEATURES 审计；不得删除历史操作以回退约束。
ALTER TABLE organization_access.member_account_operation
    DROP CONSTRAINT member_account_operation_operation_check;
ALTER TABLE organization_access.member_account_operation
    ADD CONSTRAINT member_account_operation_operation_check
    CHECK (operation IN ('CREATE', 'RESET', 'DISABLE', 'ENABLE', 'FEATURES'));
