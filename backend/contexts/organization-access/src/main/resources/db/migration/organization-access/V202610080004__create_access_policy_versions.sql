-- organization-access：策略草稿、冻结版本与预览事实；有效授权仍只有 member_assistant_authorization。
-- 不迁移模拟策略、不自动授予成员；正文和审计不能无损自动回滚，删除需显式评估历史恢复。
CREATE TABLE organization_access.access_policy (
    tenant_id BIGINT NOT NULL REFERENCES organization_access.tenant_lifecycle(tenant_id),
    policy_id BIGINT NOT NULL CHECK (policy_id > 0),
    name VARCHAR(80) NOT NULL CHECK (char_length(name) BETWEEN 2 AND 80),
    PRIMARY KEY (tenant_id, policy_id), UNIQUE (tenant_id, name)
);
CREATE TABLE organization_access.access_policy_version (
    tenant_id BIGINT NOT NULL, policy_id BIGINT NOT NULL, version_id BIGINT NOT NULL CHECK (version_id > 0),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    assistant_id BIGINT NOT NULL CHECK (assistant_id > 0),
    assistant_version_id BIGINT NOT NULL CHECK (assistant_version_id > 0), assistant_name VARCHAR(200) NOT NULL,
    knowledge_space_id BIGINT NOT NULL CHECK (knowledge_space_id > 0), knowledge_space_name VARCHAR(200) NOT NULL,
    authorization_revision BIGINT NOT NULL CHECK (authorization_revision >= 0),
    tenant_feature_version BIGINT NOT NULL CHECK (tenant_feature_version >= 0),
    preview_fingerprint VARCHAR(64), allow_count INTEGER NOT NULL DEFAULT 0 CHECK (allow_count >= 0),
    deny_count INTEGER NOT NULL DEFAULT 0 CHECK (deny_count >= 0),
    leak_count INTEGER NOT NULL DEFAULT 0 CHECK (leak_count >= 0), previewed_at TIMESTAMPTZ,
    published_authorization_revision BIGINT,
    PRIMARY KEY (tenant_id, policy_id, version_id), UNIQUE (tenant_id, policy_id, version_number),
    FOREIGN KEY (tenant_id, policy_id) REFERENCES organization_access.access_policy(tenant_id, policy_id),
    CHECK ((preview_fingerprint IS NULL) = (previewed_at IS NULL)),
    CHECK ((status = 'DRAFT') = (published_authorization_revision IS NULL))
);
CREATE UNIQUE INDEX access_policy_one_draft ON organization_access.access_policy_version(tenant_id, policy_id) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX access_policy_one_published ON organization_access.access_policy_version(tenant_id, policy_id) WHERE status = 'PUBLISHED';
CREATE TABLE organization_access.access_policy_version_member (
    tenant_id BIGINT NOT NULL, policy_id BIGINT NOT NULL, version_id BIGINT NOT NULL, membership_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, policy_id, version_id, membership_id),
    FOREIGN KEY (tenant_id, policy_id, version_id) REFERENCES organization_access.access_policy_version(tenant_id, policy_id, version_id),
    FOREIGN KEY (tenant_id, membership_id) REFERENCES organization_access.tenant_membership(tenant_id, membership_id)
);
CREATE TABLE organization_access.access_policy_audit (
    tenant_id BIGINT NOT NULL, actor_subject_id BIGINT NOT NULL, policy_id BIGINT NOT NULL, version_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL CHECK (action IN ('CREATE', 'DRAFT', 'SAVE', 'PREVIEW', 'PUBLISH')),
    version_revision BIGINT NOT NULL CHECK (version_revision >= 0), occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, version_id, action, version_revision),
    FOREIGN KEY (tenant_id, policy_id, version_id) REFERENCES organization_access.access_policy_version(tenant_id, policy_id, version_id)
);
