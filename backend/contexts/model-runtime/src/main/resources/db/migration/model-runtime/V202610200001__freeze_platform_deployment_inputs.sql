-- 所属上下文：model-runtime。目的：保存 ADR-0100 的完整草稿声明及 RERANK 参数。
-- 影响：只为新声明增加可空列，历史事实不回填。拟授权不是生效 ACL；回滚将丢失新声明，不能无损回滚。
ALTER TABLE model_runtime.platform_model_deployment
    ADD COLUMN parameter_source VARCHAR(160), ADD COLUMN residency VARCHAR(120),
    ADD COLUMN rerank_candidate_limit INTEGER, ADD COLUMN planned_tenant_ids BIGINT[],
    ADD COLUMN candidate_discovery_id BIGINT REFERENCES model_runtime.platform_provider_model_discovery(id),
    ADD COLUMN candidate_display_name VARCHAR(200), ADD COLUMN candidate_classification VARCHAR(64),
    ADD COLUMN declared_by_subject_id BIGINT, ADD COLUMN declared_at TIMESTAMPTZ,
    DROP CONSTRAINT ck_platform_model_deployment_type,
    DROP CONSTRAINT ck_platform_model_deployment_type_contract,
    ADD CONSTRAINT ck_platform_model_deployment_type CHECK (model_type IN ('CHAT', 'EMBEDDING', 'RERANK')),
    ADD CONSTRAINT ck_platform_model_deployment_type_contract CHECK (
        (model_type = 'CHAT' AND 'CHAT' = ANY(capabilities) AND output_limit > 0 AND output_limit IS NOT NULL
            AND embedding_dimension IS NULL AND rerank_candidate_limit IS NULL)
        OR (model_type = 'EMBEDDING' AND 'EMBEDDING' = ANY(capabilities) AND embedding_dimension > 0
            AND embedding_dimension IS NOT NULL AND output_limit IS NULL AND rerank_candidate_limit IS NULL)
        OR (model_type = 'RERANK' AND capabilities::TEXT[] = ARRAY['RERANK']::TEXT[] AND rerank_candidate_limit > 0
            AND rerank_candidate_limit IS NOT NULL AND output_limit IS NULL AND embedding_dimension IS NULL)),
    ADD CONSTRAINT ck_platform_model_deployment_inputs CHECK (
        (parameter_source IS NULL AND residency IS NULL AND planned_tenant_ids IS NULL AND candidate_discovery_id IS NULL
            AND candidate_display_name IS NULL AND candidate_classification IS NULL AND declared_by_subject_id IS NULL AND declared_at IS NULL)
        OR (parameter_source IS NOT NULL AND length(parameter_source) > 0 AND residency IS NOT NULL AND length(residency) > 0
            AND planned_tenant_ids IS NOT NULL AND cardinality(planned_tenant_ids) BETWEEN 1 AND 1000
            AND candidate_discovery_id IS NOT NULL AND candidate_classification IS NOT NULL
            AND declared_by_subject_id IS NOT NULL AND declared_by_subject_id > 0 AND declared_at IS NOT NULL));
ALTER TABLE model_runtime.platform_discovered_model DROP CONSTRAINT ck_platform_discovered_model_confirmation,
    ADD CONSTRAINT ck_platform_discovered_model_confirmation CHECK (
        (classification = 'REQUIRES_ADMIN_CONFIRMATION' AND model_type IS NULL AND cardinality(capabilities) = 0)
        OR (classification <> 'REQUIRES_ADMIN_CONFIRMATION' AND (
            (model_type = 'CHAT' AND 'CHAT' = ANY(capabilities)) OR (model_type = 'EMBEDDING' AND 'EMBEDDING' = ANY(capabilities))
            OR (model_type = 'RERANK' AND capabilities::TEXT[] = ARRAY['RERANK']::TEXT[]))));
ALTER TABLE model_runtime.platform_model_candidate_confirmation DROP CONSTRAINT ck_platform_model_candidate_confirmation_type,
    ADD CONSTRAINT ck_platform_model_candidate_confirmation_type CHECK (
        (model_type = 'CHAT' AND capabilities::TEXT[] = ARRAY['CHAT']::TEXT[])
        OR (model_type = 'EMBEDDING' AND capabilities::TEXT[] = ARRAY['EMBEDDING']::TEXT[])
        OR (model_type = 'RERANK' AND capabilities::TEXT[] = ARRAY['RERANK']::TEXT[]));
