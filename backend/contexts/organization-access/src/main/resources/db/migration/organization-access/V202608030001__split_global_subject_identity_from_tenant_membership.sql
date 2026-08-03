-- 所属上下文：organization-access。
-- 目的：将历史成员凭据拆分为唯一全局主体凭据与仅引用主体的租户成员资格。
-- 影响：拒绝存在歧义登录名或冲突主体凭据的数据；成功后删除成员表的登录名和密码摘要列。
-- 回滚：不可自动回滚，因删除的成员凭据列只能从迁移前备份恢复。

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM organization_access.membership_credential
        GROUP BY subject_id
        HAVING COUNT(DISTINCT login_name) > 1
            OR COUNT(DISTINCT password_hash) > 1
            OR COUNT(DISTINCT status) > 1
    ) THEN
        RAISE EXCEPTION
            '无法迁移全局主体：同一 subject_id 存在冲突登录名、凭据摘要或状态';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM organization_access.membership_credential
        GROUP BY login_name
        HAVING COUNT(DISTINCT subject_id) > 1
    ) THEN
        RAISE EXCEPTION
            '无法迁移全局主体：同一登录名对应多个 subject_id';
    END IF;
END
$$;

CREATE TABLE organization_access.subject_identity (
    subject_id BIGINT PRIMARY KEY,
    login_name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_oa_subject_identity_login_name UNIQUE (login_name),
    CONSTRAINT ck_oa_subject_identity_login_name
        CHECK (login_name ~ '^[a-z][a-z0-9._-]{2,63}$'),
    CONSTRAINT ck_oa_subject_identity_password_hash
        CHECK (char_length(password_hash) BETWEEN 8 AND 256),
    CONSTRAINT ck_oa_subject_identity_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_oa_subject_identity_version
        CHECK (version >= 0)
);

INSERT INTO organization_access.subject_identity (
    subject_id,
    login_name,
    password_hash,
    status,
    version,
    created_at,
    updated_at
)
SELECT DISTINCT ON (subject_id)
    subject_id,
    login_name,
    password_hash,
    CASE status
        WHEN 'ENABLED' THEN 'ACTIVE'
        WHEN 'DISABLED' THEN 'DISABLED'
    END,
    version,
    created_at,
    updated_at
FROM organization_access.membership_credential
ORDER BY subject_id, created_at, membership_id;

ALTER TABLE organization_access.membership_credential
    DROP CONSTRAINT uk_oa_membership_tenant_login,
    DROP CONSTRAINT ck_oa_membership_login_name,
    DROP CONSTRAINT ck_oa_membership_password_hash,
    DROP COLUMN login_name,
    DROP COLUMN password_hash,
    ADD COLUMN membership_role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    ADD CONSTRAINT fk_oa_membership_subject
        FOREIGN KEY (subject_id)
        REFERENCES organization_access.subject_identity (subject_id),
    ADD CONSTRAINT ck_oa_membership_role
        CHECK (membership_role IN ('MEMBER', 'TENANT_ADMINISTRATOR'));

ALTER TABLE organization_access.membership_credential
    RENAME TO tenant_membership;
