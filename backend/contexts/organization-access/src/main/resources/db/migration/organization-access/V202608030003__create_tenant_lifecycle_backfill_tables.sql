-- 所属上下文：organization-access。
-- 目的：保存租户生命周期根及其最小发件箱事实，为受控历史回填提供原子提交目标。
-- 影响：不包含人工 `tenantId → SubjectId` 映射；映射只在部署事务内短暂使用。
-- 回滚：不可自动回滚，已提交的生命周期和发件箱事实只能经受控恢复流程处理。

CREATE TABLE organization_access.tenant_lifecycle (
    tenant_id BIGINT PRIMARY KEY,
    tenant_code VARCHAR(63) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    initial_administrator_subject_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_oa_tenant_lifecycle_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES organization_access.tenant (tenant_id),
    CONSTRAINT fk_oa_tenant_lifecycle_initial_administrator
        FOREIGN KEY (initial_administrator_subject_id)
        REFERENCES organization_access.subject_identity (subject_id),
    CONSTRAINT ck_oa_tenant_lifecycle_status
        CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_oa_tenant_lifecycle_version
        CHECK (version >= 0)
);

CREATE TABLE organization_access.tenant_lifecycle_outbox (
    event_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL,
    lifecycle_version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_oa_tenant_lifecycle_outbox_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES organization_access.tenant_lifecycle (tenant_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_oa_tenant_lifecycle_outbox_version
        UNIQUE (tenant_id, lifecycle_version),
    CONSTRAINT ck_oa_tenant_lifecycle_outbox_type
        CHECK (event_type = 'TENANT_LIFECYCLE_CHANGED'),
    CONSTRAINT ck_oa_tenant_lifecycle_outbox_status
        CHECK (lifecycle_status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_oa_tenant_lifecycle_outbox_version
        CHECK (lifecycle_version >= 0)
);
