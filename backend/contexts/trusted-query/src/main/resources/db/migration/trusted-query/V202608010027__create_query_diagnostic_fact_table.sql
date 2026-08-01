-- trusted-query：保存终态查询的最小内部诊断事实；可随 `query_run` 删除级联移除。
-- 本迁移可前向执行，不提供回滚脚本。
CREATE TABLE trusted_query.query_diagnostic_fact (
    tenant_id BIGINT NOT NULL,
    query_run_id BIGINT NOT NULL,
    candidate_collection VARCHAR(16) NOT NULL,
    candidate_count INTEGER,
    retrieval_authorization VARCHAR(16) NOT NULL,
    chat_model_revision BIGINT NOT NULL,
    embedding_model_revision BIGINT NOT NULL,
    citation_validation VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, query_run_id),
    CONSTRAINT fk_query_diagnostic_fact_run
        FOREIGN KEY (tenant_id, query_run_id)
        REFERENCES trusted_query.query_run (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_query_diagnostic_fact_candidate
        CHECK (
            (candidate_collection = 'COLLECTED' AND candidate_count >= 0)
            OR (candidate_collection = 'NOT_COLLECTED' AND candidate_count IS NULL)
        ),
    CONSTRAINT ck_query_diagnostic_fact_retrieval
        CHECK (retrieval_authorization = 'AUTHORIZED'),
    CONSTRAINT ck_query_diagnostic_fact_revisions
        CHECK (chat_model_revision > 0 AND embedding_model_revision > 0),
    CONSTRAINT ck_query_diagnostic_fact_citation
        CHECK (citation_validation IN ('VALIDATED', 'NOT_APPLICABLE'))
);
