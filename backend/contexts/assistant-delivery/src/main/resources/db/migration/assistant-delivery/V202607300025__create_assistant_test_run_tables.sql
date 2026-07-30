-- 所属上下文：assistant-delivery。
-- 目的：持久化草稿助手测试的独立配额、冻结依赖和最小终态引用，不混入普通查询事实。
-- 影响：新增两张表；不可与 `QueryRun` 共用，回滚需先删除全部草稿测试历史。
CREATE TABLE assistant_delivery.assistant_test_run (
    tenant_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    assistant_version_id BIGINT NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    index_version_id BIGINT NOT NULL,
    index_version_revision BIGINT NOT NULL,
    chat_configuration_id BIGINT NOT NULL,
    chat_configuration_revision BIGINT NOT NULL,
    chat_catalog_entry_id BIGINT NOT NULL,
    chat_catalog_entry_revision BIGINT NOT NULL,
    chat_output_limit INTEGER NOT NULL,
    embedding_configuration_id BIGINT NOT NULL,
    embedding_configuration_revision BIGINT NOT NULL,
    embedding_catalog_entry_id BIGINT NOT NULL,
    embedding_catalog_entry_revision BIGINT NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    system_instruction VARCHAR(32000) NOT NULL,
    max_output_tokens INTEGER NOT NULL,
    retrieval_limit INTEGER NOT NULL,
    minimum_score DOUBLE PRECISION NOT NULL,
    citation_policy VARCHAR(32) NOT NULL,
    refusal_policy VARCHAR(64) NOT NULL,
    question_digest CHAR(64) NOT NULL,
    quota_period_start TIMESTAMPTZ NOT NULL,
    quota_period_end TIMESTAMPTZ NOT NULL,
    daily_test_limit INTEGER NOT NULL,
    daily_test_ordinal INTEGER NOT NULL,
    concurrent_test_limit INTEGER NOT NULL,
    accepted_lease_until TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    refusal_reason VARCHAR(64),
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    total_tokens INTEGER NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT pk_assistant_test_run PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_assistant_test_run_dependencies CHECK (
        assistant_version_id > 0 AND knowledge_space_id > 0 AND index_version_id > 0
        AND index_version_revision > 0 AND chat_configuration_id > 0
        AND chat_configuration_revision > 0 AND chat_catalog_entry_id > 0
        AND chat_catalog_entry_revision > 0 AND chat_output_limit > 0
        AND embedding_configuration_id > 0 AND embedding_configuration_revision > 0
        AND embedding_catalog_entry_id > 0 AND embedding_catalog_entry_revision > 0
        AND embedding_dimension > 0
    ),
    CONSTRAINT ck_assistant_test_run_parameters CHECK (
        max_output_tokens BETWEEN 1 AND chat_output_limit
        AND retrieval_limit BETWEEN 1 AND 20
        AND minimum_score BETWEEN 0.0 AND 1.0
        AND citation_policy = 'REQUIRED'
        AND refusal_policy = 'INSUFFICIENT_EVIDENCE'
    ),
    CONSTRAINT ck_assistant_test_run_quota CHECK (
        quota_period_end > quota_period_start
        AND daily_test_limit > 0
        AND daily_test_ordinal BETWEEN 1 AND daily_test_limit
        AND concurrent_test_limit > 0
        AND accepted_lease_until >= created_at
    ),
    CONSTRAINT ck_assistant_test_run_usage CHECK (
        input_tokens >= 0 AND output_tokens >= 0 AND total_tokens = input_tokens + output_tokens
    ),
    CONSTRAINT ck_assistant_test_run_status CHECK (status IN ('RUNNING', 'ANSWERED', 'REFUSED', 'FAILED')),
    CONSTRAINT ck_assistant_test_run_terminal CHECK (
        (status = 'RUNNING' AND refusal_reason IS NULL AND input_tokens = 0
            AND output_tokens = 0 AND total_tokens = 0 AND version = 0 AND completed_at IS NULL)
        OR (status = 'ANSWERED' AND refusal_reason IS NULL AND completed_at IS NOT NULL AND version > 0)
        OR (status = 'REFUSED' AND refusal_reason = 'INSUFFICIENT_EVIDENCE' AND input_tokens = 0
            AND output_tokens = 0 AND total_tokens = 0 AND completed_at IS NOT NULL AND version > 0)
        OR (status = 'FAILED' AND refusal_reason IS NULL AND input_tokens = 0
            AND output_tokens = 0 AND total_tokens = 0 AND completed_at IS NOT NULL AND version > 0)
    )
);

CREATE INDEX idx_assistant_test_run_tenant_day
    ON assistant_delivery.assistant_test_run (tenant_id, created_at);

CREATE INDEX idx_assistant_test_run_active_lease
    ON assistant_delivery.assistant_test_run (tenant_id, accepted_lease_until)
    WHERE status = 'RUNNING';

CREATE TABLE assistant_delivery.assistant_test_run_citation (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    assistant_test_run_id BIGINT NOT NULL,
    sequence INTEGER NOT NULL,
    data_source_id BIGINT NOT NULL,
    content_version INTEGER NOT NULL,
    chunk_id VARCHAR(320) NOT NULL,
    ordinal INTEGER NOT NULL,
    CONSTRAINT uk_assistant_test_run_citation_sequence
        UNIQUE (tenant_id, assistant_test_run_id, sequence),
    CONSTRAINT fk_assistant_test_run_citation_run
        FOREIGN KEY (tenant_id, assistant_test_run_id)
        REFERENCES assistant_delivery.assistant_test_run (tenant_id, id),
    CONSTRAINT ck_assistant_test_run_citation_values CHECK (
        sequence > 0 AND data_source_id > 0 AND content_version > 0
        AND ordinal >= 0 AND btrim(chunk_id) <> ''
    )
);
