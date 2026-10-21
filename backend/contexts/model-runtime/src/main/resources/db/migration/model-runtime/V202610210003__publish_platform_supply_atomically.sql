-- model-runtime：平台部署独立发布时原子冻结目录、精确租户授权与审计。
-- 不给旧目录或计划租户补造授权，不修改既有业务行；旧事实缺新发布证明时不可新使用。
-- RERANK 单独冻结候选上限，不能沿用 Embedding 维度。相同模型允许不同不可变部署快照。
-- 回滚须先关闭供应写入并保留已发布／撤权事实，不能在线删除目录或租户配置。
ALTER TABLE model_runtime.controlled_model_catalog_entry
    ADD COLUMN rerank_candidate_limit INTEGER,
    DROP CONSTRAINT ck_controlled_model_catalog_model_type,
    DROP CONSTRAINT ck_controlled_model_catalog_capabilities,
    ADD CONSTRAINT ck_controlled_model_catalog_model_type CHECK (model_type IN ('CHAT', 'EMBEDDING', 'RERANK')),
    ADD CONSTRAINT ck_controlled_model_catalog_capabilities CHECK (
        (context_window IS NULL AND output_limit IS NULL AND embedding_dimension IS NULL AND rerank_candidate_limit IS NULL)
        OR (model_type = 'CHAT' AND context_window IS NOT NULL AND context_window > 0
            AND output_limit IS NOT NULL AND output_limit BETWEEN 1 AND context_window
            AND embedding_dimension IS NULL AND rerank_candidate_limit IS NULL)
        OR (model_type = 'EMBEDDING' AND context_window IS NOT NULL AND context_window > 0
            AND embedding_dimension IS NOT NULL AND embedding_dimension > 0
            AND output_limit IS NULL AND rerank_candidate_limit IS NULL)
        OR (model_type = 'RERANK' AND context_window IS NOT NULL AND context_window > 0
            AND rerank_candidate_limit IS NOT NULL AND rerank_candidate_limit > 0
            AND output_limit IS NULL AND embedding_dimension IS NULL));
DROP INDEX model_runtime.uk_controlled_model_catalog_entry_unique;
CREATE UNIQUE INDEX uk_controlled_model_catalog_legacy_model
    ON model_runtime.controlled_model_catalog_entry(provider_kind, endpoint, model_code, model_type)
    WHERE source_platform_deployment_id IS NULL;

CREATE UNIQUE INDEX uk_controlled_model_catalog_source_revision
    ON model_runtime.controlled_model_catalog_entry(source_platform_deployment_id, source_platform_deployment_revision)
    WHERE source_platform_deployment_id IS NOT NULL;

CREATE TABLE model_runtime.platform_model_deployment_publication (
    id BIGINT PRIMARY KEY,
    deployment_id BIGINT NOT NULL REFERENCES model_runtime.platform_model_deployment(id),
    deployment_revision BIGINT NOT NULL CHECK (deployment_revision > 0),
    verification_id BIGINT NOT NULL UNIQUE REFERENCES model_runtime.platform_model_capability_verification(id),
    catalog_entry_id BIGINT NOT NULL UNIQUE REFERENCES model_runtime.controlled_model_catalog_entry(id),
    actor_subject_id BIGINT NOT NULL CHECK (actor_subject_id > 0),
    actor_permission_version BIGINT NOT NULL CHECK (actor_permission_version >= 0),
    inputs_digest VARCHAR(64) NOT NULL CHECK (inputs_digest ~ '^[0-9a-f]{64}$'),
    published_at TIMESTAMPTZ NOT NULL,
    UNIQUE (deployment_id, deployment_revision)
);
CREATE TABLE model_runtime.platform_model_supply_grant (
    publication_id BIGINT NOT NULL REFERENCES model_runtime.platform_model_deployment_publication(id),
    tenant_id BIGINT NOT NULL CHECK (tenant_id > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    version BIGINT NOT NULL CHECK (version >= 0),
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    PRIMARY KEY (publication_id, tenant_id),
    CHECK ((status = 'ACTIVE' AND revoked_at IS NULL) OR (status = 'REVOKED' AND revoked_at IS NOT NULL))
);
CREATE INDEX platform_supply_tenant_active ON model_runtime.platform_model_supply_grant(tenant_id, publication_id)
    WHERE status = 'ACTIVE';
CREATE TABLE model_runtime.platform_model_supply_audit (
    id BIGINT PRIMARY KEY,
    deployment_id BIGINT NOT NULL REFERENCES model_runtime.platform_model_deployment(id),
    publication_id BIGINT REFERENCES model_runtime.platform_model_deployment_publication(id),
    tenant_id BIGINT,
    actor_subject_id BIGINT NOT NULL CHECK (actor_subject_id > 0),
    actor_permission_version BIGINT NOT NULL CHECK (actor_permission_version >= 0),
    action VARCHAR(16) NOT NULL CHECK (action IN ('PUBLISH', 'REVOKE', 'DISABLE')),
    occurred_at TIMESTAMPTZ NOT NULL,
    CHECK (action <> 'REVOKE' OR (tenant_id IS NOT NULL AND publication_id IS NOT NULL))
);
