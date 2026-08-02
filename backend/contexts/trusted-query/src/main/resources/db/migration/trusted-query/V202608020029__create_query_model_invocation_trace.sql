-- 所属上下文：trusted-query。
-- 目的：将受控聊天路由的最小内部调用事实与查询终态原子留存。
-- 影响：新增内部轨迹表；回滚前必须删除全部查询轨迹，不能影响公开诊断投影。
CREATE TABLE trusted_query.query_model_invocation_trace (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    query_run_id BIGINT NOT NULL,
    attempt_sequence SMALLINT NOT NULL,
    configuration_id BIGINT NOT NULL,
    configuration_revision BIGINT NOT NULL,
    catalog_entry_id BIGINT NOT NULL,
    catalog_entry_revision BIGINT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    total_tokens INTEGER NOT NULL,
    latency_millis BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_query_model_invocation_trace_sequence UNIQUE (tenant_id, query_run_id, attempt_sequence),
    CONSTRAINT fk_query_model_invocation_trace_run
        FOREIGN KEY (tenant_id, query_run_id)
        REFERENCES trusted_query.query_run (tenant_id, id),
    CONSTRAINT ck_query_model_invocation_trace_values CHECK (
        attempt_sequence BETWEEN 1 AND 3
        AND configuration_id > 0
        AND configuration_revision > 0
        AND catalog_entry_id > 0
        AND catalog_entry_revision > 0
        AND outcome IN ('SUCCEEDED', 'TRANSIENT_FAILURE')
        AND input_tokens >= 0
        AND output_tokens >= 0
        AND total_tokens >= 0
        AND (total_tokens = 0 OR total_tokens >= input_tokens + output_tokens)
        AND latency_millis >= 0
    )
);
