-- 所属上下文：evaluation-operations；目的：持久化冻结评测运行的计数、指标和终态；影响：新增无正文运行事实表；回滚：确认没有发布决定引用后删除该表。
CREATE TABLE evaluation_operations.evaluation_run (
    tenant_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    assistant_version_id BIGINT NOT NULL,
    assistant_version_revision BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    dataset_version BIGINT NOT NULL,
    dataset_content_digest CHAR(64) NOT NULL,
    gate_policy_version VARCHAR(128) NOT NULL,
    minimum_recall_at_10 DOUBLE PRECISION NOT NULL,
    minimum_citation_correctness DOUBLE PRECISION NOT NULL,
    minimum_correct_refusal_rate DOUBLE PRECISION NOT NULL,
    model_route_fingerprint CHAR(64) NOT NULL,
    metrics_implementation_version VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expected_evidence_count INTEGER NOT NULL,
    retrieved_expected_evidence_count INTEGER NOT NULL,
    citation_count INTEGER NOT NULL,
    correct_citation_count INTEGER NOT NULL,
    expected_refusal_count INTEGER NOT NULL,
    correct_refusal_count INTEGER NOT NULL,
    maximum_retrieved_candidate_count INTEGER NOT NULL,
    failed_entry_count INTEGER NOT NULL,
    unauthorized_evidence BOOLEAN NOT NULL,
    metrics_computable BOOLEAN NOT NULL,
    recall_at_10 DOUBLE PRECISION NOT NULL,
    citation_correctness DOUBLE PRECISION NOT NULL,
    correct_refusal_rate DOUBLE PRECISION NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_evaluation_run PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_evaluation_run_dataset_digest CHECK (dataset_content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evaluation_run_route_fingerprint CHECK (model_route_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evaluation_run_status CHECK (status IN ('PASSED', 'FAILED')),
    CONSTRAINT ck_evaluation_run_counts CHECK (
        expected_evidence_count >= 0 AND retrieved_expected_evidence_count BETWEEN 0 AND expected_evidence_count
        AND citation_count >= 0 AND correct_citation_count BETWEEN 0 AND citation_count
        AND expected_refusal_count >= 0 AND correct_refusal_count BETWEEN 0 AND expected_refusal_count
        AND maximum_retrieved_candidate_count >= 0 AND failed_entry_count >= 0
    ),
    CONSTRAINT ck_evaluation_run_rates CHECK (
        minimum_recall_at_10 > 0 AND minimum_recall_at_10 <= 1
        AND minimum_citation_correctness > 0 AND minimum_citation_correctness <= 1
        AND minimum_correct_refusal_rate > 0 AND minimum_correct_refusal_rate <= 1
        AND recall_at_10 >= 0 AND recall_at_10 <= 1
        AND citation_correctness >= 0 AND citation_correctness <= 1
        AND correct_refusal_rate >= 0 AND correct_refusal_rate <= 1
    ),
    CONSTRAINT ck_evaluation_run_version CHECK (version > 0)
);

CREATE INDEX ix_evaluation_run_tenant_completed
    ON evaluation_operations.evaluation_run (tenant_id, completed_at DESC, id DESC);
