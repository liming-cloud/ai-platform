-- 所属上下文：organization-access，本地研发 profile。
-- 目的：纯本地空库建立唯一平台管理员，使人工能够从页面完成其余产品初始化。
-- 影响：仅在主体、租户和成员均为空时，原子写入主体、授权修订和固定平台角色；数据库只保存 PBKDF2 摘要。
-- 回滚：不可自动回滚；仅允许重建可丢弃的本地数据库，不得用于共享、测试、预发布或生产环境。
WITH local_subject AS (
    INSERT INTO organization_access.subject_identity (
        subject_id,
        login_name,
        password_hash,
        status,
        version,
        created_at,
        updated_at
    )
    SELECT
        1,
        'platform.root',
        'pbkdf2-sha256$v1$600000$Zm91bmRhdGlvbi1sb2NhbA$Ma5QciIbHoKQTuqaT7xmt3U1PtE7eZtZGTufYgiBAHY',
        'ACTIVE',
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    WHERE NOT EXISTS (
        SELECT 1 FROM organization_access.subject_identity
    )
      AND NOT EXISTS (
        SELECT 1 FROM organization_access.tenant
    )
      AND NOT EXISTS (
        SELECT 1 FROM organization_access.tenant_membership
    )
    RETURNING subject_id
), local_authorization AS (
    INSERT INTO organization_access.platform_subject_authorization (
        subject_id,
        permission_version,
        version,
        created_at,
        updated_at
    )
    SELECT
        subject_id,
        1,
        0,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    FROM local_subject
    RETURNING subject_id
)
INSERT INTO organization_access.platform_role_binding (
    binding_id,
    subject_id,
    role_code,
    status,
    version,
    created_at,
    revoked_at
)
SELECT
    subject_id,
    subject_id,
    'PLATFORM_ADMINISTRATOR',
    'ACTIVE',
    0,
    CURRENT_TIMESTAMP,
    NULL
FROM local_authorization;
