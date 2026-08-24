-- 所属上下文：model-runtime。目的：记录管理员对未知模型能力的显式确认，并使确认后的候选可部署。
-- 影响：确认作为独立不可变事实附着到发现候选；回滚会丢失确认主体与时间，不能无损回滚。
CREATE TABLE model_runtime.platform_model_candidate_confirmation (
    discovery_id BIGINT NOT NULL
        REFERENCES model_runtime.platform_provider_model_discovery (id),
    model_code VARCHAR(160) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    capabilities VARCHAR(64)[] NOT NULL,
    confirmed_by_subject_id BIGINT NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (discovery_id, model_code),
    CONSTRAINT ck_platform_model_candidate_confirmation_type CHECK (
        (model_type = 'CHAT' AND capabilities = ARRAY['CHAT']::VARCHAR[])
        OR
        (model_type = 'EMBEDDING' AND capabilities = ARRAY['EMBEDDING']::VARCHAR[])
    )
);
