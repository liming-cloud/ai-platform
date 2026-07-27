CREATE SCHEMA IF NOT EXISTS trusted_query;

CREATE TABLE trusted_query.query_run (
    tenant_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    assistant_version_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    question_digest CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    answer TEXT,
    refusal_reason VARCHAR(64),
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (id),
    CONSTRAINT uk_query_run_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_query_run_assistant_version
        FOREIGN KEY (tenant_id, assistant_version_id)
        REFERENCES assistant_delivery.assistant_version (tenant_id, id),
    CONSTRAINT fk_query_run_membership
        FOREIGN KEY (tenant_id, requested_by)
        REFERENCES organization_access.membership_credential (
            tenant_id,
            membership_id
        ),
    CONSTRAINT ck_query_run_question_digest
        CHECK (question_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_query_run_status
        CHECK (status IN ('RUNNING', 'ANSWERED', 'REFUSED', 'FAILED')),
    CONSTRAINT ck_query_run_usage
        CHECK (
            input_tokens >= 0
            AND output_tokens >= 0
            AND (
                total_tokens = 0
                OR total_tokens >= input_tokens + output_tokens
            )
        ),
    CONSTRAINT ck_query_run_state
        CHECK (
            (
                status = 'RUNNING'
                AND answer IS NULL
                AND refusal_reason IS NULL
                AND version = 0
                AND completed_at IS NULL
            )
            OR (
                status = 'ANSWERED'
                AND answer IS NOT NULL
                AND refusal_reason IS NULL
                AND version > 0
                AND completed_at IS NOT NULL
            )
            OR (
                status = 'REFUSED'
                AND answer IS NULL
                AND refusal_reason = 'INSUFFICIENT_EVIDENCE'
                AND version > 0
                AND completed_at IS NOT NULL
            )
            OR (
                status = 'FAILED'
                AND answer IS NULL
                AND refusal_reason IS NULL
                AND version > 0
                AND completed_at IS NOT NULL
            )
        )
);

CREATE TABLE trusted_query.query_run_request (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    query_run_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_query_run_request_tenant_key
        UNIQUE (tenant_id, idempotency_key_hash),
    CONSTRAINT fk_query_run_request_run
        FOREIGN KEY (tenant_id, query_run_id)
        REFERENCES trusted_query.query_run (tenant_id, id),
    CONSTRAINT ck_query_run_request_key
        CHECK (idempotency_key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_query_run_request_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE TABLE trusted_query.query_citation (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    query_run_id BIGINT NOT NULL,
    sequence INTEGER NOT NULL,
    data_source_id BIGINT NOT NULL,
    content_version INTEGER NOT NULL,
    chunk_id CHAR(64) NOT NULL,
    ordinal INTEGER NOT NULL,
    quote TEXT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    CONSTRAINT uk_query_citation_run_sequence
        UNIQUE (tenant_id, query_run_id, sequence),
    CONSTRAINT fk_query_citation_run
        FOREIGN KEY (tenant_id, query_run_id)
        REFERENCES trusted_query.query_run (tenant_id, id),
    CONSTRAINT ck_query_citation_sequence CHECK (sequence > 0),
    CONSTRAINT ck_query_citation_content_version CHECK (content_version > 0),
    CONSTRAINT ck_query_citation_ordinal CHECK (ordinal >= 0),
    CONSTRAINT ck_query_citation_chunk CHECK (chunk_id ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_query_citation_score CHECK (score >= -1 AND score <= 1)
);

CREATE INDEX ix_query_run_tenant_created
    ON trusted_query.query_run (tenant_id, created_at DESC, id DESC);
