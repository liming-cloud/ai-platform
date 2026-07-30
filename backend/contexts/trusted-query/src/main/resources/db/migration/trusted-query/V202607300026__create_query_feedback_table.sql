-- 所属上下文：trusted-query；目的：保存不可变的最小查询反馈事实，并跟随查询运行删除；影响：新增反馈表和查询运行请求成员组合唯一约束；回滚：可先删除反馈表，再删除组合唯一约束。
ALTER TABLE trusted_query.query_run
    ADD CONSTRAINT uk_query_run_tenant_id_requester
        UNIQUE (tenant_id, id, requested_by);

CREATE TABLE trusted_query.query_feedback (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    query_run_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_query_feedback_run_requester
        UNIQUE (tenant_id, query_run_id, requested_by),
    CONSTRAINT fk_query_feedback_run_requester
        FOREIGN KEY (tenant_id, query_run_id, requested_by)
        REFERENCES trusted_query.query_run (tenant_id, id, requested_by)
        ON DELETE CASCADE,
    CONSTRAINT ck_query_feedback_outcome
        CHECK (outcome IN ('POSITIVE', 'NEGATIVE')),
    CONSTRAINT ck_query_feedback_reason
        CHECK (
            (outcome = 'POSITIVE' AND reason = 'HELPFUL')
            OR (outcome = 'NEGATIVE' AND reason IN (
                'INCORRECT', 'INSUFFICIENT', 'IRRELEVANT'
            ))
        )
);

CREATE INDEX ix_query_feedback_tenant_created
    ON trusted_query.query_feedback (tenant_id, created_at DESC, id DESC);
