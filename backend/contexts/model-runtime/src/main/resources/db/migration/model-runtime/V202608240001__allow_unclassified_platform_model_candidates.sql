-- 所属上下文：model-runtime。目的：允许权威发现保存待管理员确认且未猜测能力的平台模型候选。
-- 影响：平台候选新增分类并允许类型为空；历史候选标记为供应商已确认。回滚会丢失待确认候选。
ALTER TABLE model_runtime.platform_discovered_model
    DROP CONSTRAINT platform_discovered_model_pkey,
    DROP CONSTRAINT ck_platform_discovered_model_type,
    ALTER COLUMN model_type DROP NOT NULL,
    ADD COLUMN classification VARCHAR(48) NOT NULL DEFAULT 'CONFIRMED_BY_PROVIDER',
    ADD COLUMN candidate_key VARCHAR(32)
        GENERATED ALWAYS AS (COALESCE(model_type, 'UNCONFIRMED')) STORED;

ALTER TABLE model_runtime.platform_discovered_model
    ALTER COLUMN classification DROP DEFAULT,
    ADD CONSTRAINT platform_discovered_model_pkey
        PRIMARY KEY (discovery_id, model_code, candidate_key),
    ADD CONSTRAINT ck_platform_discovered_model_classification CHECK (
        classification IN (
            'CONFIRMED_BY_PROVIDER',
            'CONFIRMED_BY_CONTROLLED_MAPPING',
            'REQUIRES_ADMIN_CONFIRMATION'
        )
    ),
    ADD CONSTRAINT ck_platform_discovered_model_confirmation CHECK (
        (
            classification = 'REQUIRES_ADMIN_CONFIRMATION'
            AND model_type IS NULL
            AND cardinality(capabilities) = 0
        )
        OR (
            classification <> 'REQUIRES_ADMIN_CONFIRMATION'
            AND (
                (model_type = 'CHAT' AND 'CHAT' = ANY(capabilities))
                OR (model_type = 'EMBEDDING' AND 'EMBEDDING' = ANY(capabilities))
            )
        )
    );
