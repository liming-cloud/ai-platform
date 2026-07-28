-- 所属上下文：model-runtime。目的：保存平台与租户模型配置的显式使用授权及可审计撤销事实。
-- 影响：新增授权表；可通过删除该表回滚，但会丢失授权与撤销历史。
CREATE TABLE model_runtime.model_configuration_access_grant (
    id BIGINT PRIMARY KEY,
    model_configuration_id BIGINT NOT NULL REFERENCES model_runtime.model_configuration(id),
    grantee_tenant_id BIGINT NOT NULL,
    grantee_membership_id BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    CONSTRAINT model_configuration_access_grant_status_check CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT model_configuration_access_grant_version_check CHECK (version >= 0)
);

CREATE UNIQUE INDEX model_configuration_active_access_grant_uq
    ON model_runtime.model_configuration_access_grant (
        model_configuration_id, grantee_tenant_id, COALESCE(grantee_membership_id, 0)
    ) WHERE status = 'ACTIVE';

CREATE INDEX model_configuration_access_grant_lookup_idx
    ON model_runtime.model_configuration_access_grant (
        model_configuration_id, grantee_tenant_id, grantee_membership_id
    ) WHERE status = 'ACTIVE';
