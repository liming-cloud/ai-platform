-- 所属上下文：organization-access。
-- 目的：上线前拒绝活动平台管理员绑定与启用租户成员资格并存，避免迁移后继续保留越权身份组合。
-- 影响：只读检查历史事实；发现冲突时仅报告主体计数，不输出登录名、租户或内部标识。
-- 回滚：无需回滚；本迁移不修改业务数据，平台运营显式撤销一侧后可重新执行。

DO $$
DECLARE
    conflicted_subjects BIGINT;
BEGIN
    SELECT count(DISTINCT platform_binding.subject_id)
    INTO conflicted_subjects
    FROM organization_access.platform_role_binding platform_binding
    WHERE platform_binding.role_code = 'PLATFORM_ADMINISTRATOR'
      AND platform_binding.status = 'ACTIVE'
      AND EXISTS (
          SELECT 1
          FROM organization_access.tenant_membership membership
          WHERE membership.subject_id = platform_binding.subject_id
            AND membership.status = 'ENABLED'
      );

    IF conflicted_subjects > 0 THEN
        RAISE EXCEPTION '人员范围互斥预检失败，冲突主体数量：%', conflicted_subjects
            USING ERRCODE = '23514';
    END IF;
END
$$;
