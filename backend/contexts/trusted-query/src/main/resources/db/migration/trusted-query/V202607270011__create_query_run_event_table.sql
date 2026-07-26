CREATE TABLE trusted_query.query_run_event (
    tenant_id UUID NOT NULL,
    query_run_id UUID NOT NULL,
    sequence BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    answer TEXT,
    refusal_reason VARCHAR(64),
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, query_run_id, sequence),
    CONSTRAINT fk_query_run_event_run
        FOREIGN KEY (tenant_id, query_run_id)
        REFERENCES trusted_query.query_run (tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT ck_query_run_event_sequence
        CHECK (sequence > 0),
    CONSTRAINT ck_query_run_event_type
        CHECK (
            event_type IN (
                'QUERY_STARTED',
                'QUERY_ANSWERED',
                'QUERY_REFUSED',
                'QUERY_FAILED'
            )
        ),
    CONSTRAINT ck_query_run_event_status
        CHECK (status IN ('RUNNING', 'ANSWERED', 'REFUSED', 'FAILED')),
    CONSTRAINT ck_query_run_event_usage
        CHECK (
            input_tokens >= 0
            AND output_tokens >= 0
            AND (
                total_tokens = 0
                OR total_tokens >= input_tokens + output_tokens
            )
        ),
    CONSTRAINT ck_query_run_event_state
        CHECK (
            (
                event_type = 'QUERY_STARTED'
                AND sequence = 1
                AND status = 'RUNNING'
                AND answer IS NULL
                AND refusal_reason IS NULL
                AND input_tokens = 0
                AND output_tokens = 0
                AND total_tokens = 0
            )
            OR (
                event_type = 'QUERY_ANSWERED'
                AND status = 'ANSWERED'
                AND answer IS NOT NULL
                AND refusal_reason IS NULL
            )
            OR (
                event_type = 'QUERY_REFUSED'
                AND status = 'REFUSED'
                AND answer IS NULL
                AND refusal_reason IS NOT NULL
                AND input_tokens = 0
                AND output_tokens = 0
                AND total_tokens = 0
            )
            OR (
                event_type = 'QUERY_FAILED'
                AND status = 'FAILED'
                AND answer IS NULL
                AND refusal_reason IS NULL
                AND input_tokens = 0
                AND output_tokens = 0
                AND total_tokens = 0
            )
        )
);

INSERT INTO trusted_query.query_run_event (
    tenant_id,
    query_run_id,
    sequence,
    event_type,
    status,
    answer,
    refusal_reason,
    input_tokens,
    output_tokens,
    total_tokens,
    occurred_at
)
SELECT
    tenant_id,
    id,
    1,
    'QUERY_STARTED',
    'RUNNING',
    NULL,
    NULL,
    0,
    0,
    0,
    created_at
FROM trusted_query.query_run;

INSERT INTO trusted_query.query_run_event (
    tenant_id,
    query_run_id,
    sequence,
    event_type,
    status,
    answer,
    refusal_reason,
    input_tokens,
    output_tokens,
    total_tokens,
    occurred_at
)
SELECT
    tenant_id,
    id,
    2,
    CASE status
        WHEN 'ANSWERED' THEN 'QUERY_ANSWERED'
        WHEN 'REFUSED' THEN 'QUERY_REFUSED'
        ELSE 'QUERY_FAILED'
    END,
    status,
    answer,
    refusal_reason,
    input_tokens,
    output_tokens,
    total_tokens,
    completed_at
FROM trusted_query.query_run
WHERE status <> 'RUNNING';
