-- 所属上下文：assistant-delivery。
-- 目的：将受控聊天路由的最小内部调用事实与草稿助手测试终态原子留存。
-- 影响：新增内部轨迹表；回滚前必须删除全部测试轨迹，不能影响测试运行的公开结果。
CREATE TABLE assistant_delivery.assistant_test_run_model_invocation_trace (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    assistant_test_run_id BIGINT NOT NULL,
    attempt_sequence SMALLINT NOT NULL,
    configuration_id BIGINT NOT NULL,
    configuration_revision BIGINT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_assistant_test_run_model_trace_sequence
        UNIQUE (tenant_id, assistant_test_run_id, attempt_sequence),
    CONSTRAINT fk_assistant_test_run_model_trace_run
        FOREIGN KEY (tenant_id, assistant_test_run_id)
        REFERENCES assistant_delivery.assistant_test_run (tenant_id, id),
    CONSTRAINT ck_assistant_test_run_model_trace_values CHECK (
        attempt_sequence BETWEEN 1 AND 3
        AND configuration_id > 0
        AND configuration_revision > 0
        AND outcome IN ('SUCCEEDED', 'TRANSIENT_FAILURE')
    )
);
