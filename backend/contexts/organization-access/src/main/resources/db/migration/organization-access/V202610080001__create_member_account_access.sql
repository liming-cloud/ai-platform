-- 所属上下文：organization-access。依据 ADR-0093 建立成员功能、单一助手关系及无秘密操作事实。
-- 既有普通资格明确回填关闭功能和空助手范围；推进权限版本，并登记部署时需撤销的旧资格会话。
-- 保留身份、角色、状态和多租户关系；删除新表会丢失授权及审计，不能无损自动回滚。
ALTER TABLE organization_access.tenant_feature_settings
    ADD COLUMN authorization_revision BIGINT NOT NULL DEFAULT 0 CHECK (authorization_revision >= 0);
CREATE TABLE organization_access.member_conversation_features (
    tenant_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    assistant_selection BOOLEAN NOT NULL,
    conversation BOOLEAN NOT NULL,
    own_history BOOLEAN NOT NULL,
    authorized_citations BOOLEAN NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    PRIMARY KEY (tenant_id, membership_id),
    FOREIGN KEY (tenant_id, membership_id)
        REFERENCES organization_access.tenant_membership (tenant_id, membership_id) ON DELETE CASCADE
);
CREATE TABLE organization_access.member_assistant_authorization (
    tenant_id BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    assistant_id BIGINT NOT NULL CHECK (assistant_id > 0),
    PRIMARY KEY (tenant_id, membership_id, assistant_id),
    FOREIGN KEY (tenant_id, membership_id)
        REFERENCES organization_access.tenant_membership (tenant_id, membership_id) ON DELETE CASCADE
);
CREATE TABLE organization_access.member_account_operation (
    tenant_id BIGINT NOT NULL,
    actor_subject_id BIGINT NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('CREATE', 'RESET', 'DISABLE', 'ENABLE')),
    membership_id BIGINT NOT NULL,
    command_fingerprint VARCHAR(64) NOT NULL,
    result_identity_version BIGINT NOT NULL CHECK (result_identity_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, actor_subject_id, operation_key)
);
INSERT INTO organization_access.member_conversation_features
    (tenant_id, membership_id, assistant_selection, conversation, own_history, authorized_citations, version)
SELECT tenant_id, membership_id, FALSE, FALSE, FALSE, FALSE, 0
FROM organization_access.tenant_membership WHERE membership_role = 'TENANT_USER';
ALTER TABLE organization_access.tenant_membership
    ADD COLUMN member_access_pending_revocation BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE organization_access.tenant_membership
SET permission_version = permission_version + 1, version = version + 1,
    member_access_pending_revocation = TRUE
WHERE membership_role = 'TENANT_USER';
