CREATE SCHEMA IF NOT EXISTS assistant_delivery;

CREATE TABLE assistant_delivery.assistant (
    tenant_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    display_name VARCHAR(320) NOT NULL,
    latest_version_number INTEGER NOT NULL,
    version BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_assistant PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_assistant_latest_version CHECK (latest_version_number >= 0),
    CONSTRAINT ck_assistant_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_assistant_tenant_name
    ON assistant_delivery.assistant (tenant_id, lower(display_name));

CREATE INDEX idx_assistant_tenant_created
    ON assistant_delivery.assistant (tenant_id, created_at DESC, id DESC);

CREATE TABLE assistant_delivery.assistant_version (
    tenant_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    assistant_id BIGINT NOT NULL,
    version_number INTEGER NOT NULL,
    chat_deployment_id BIGINT NOT NULL,
    chat_deployment_revision BIGINT NOT NULL,
    chat_deployment_output_limit INTEGER NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    index_version_id BIGINT NOT NULL,
    index_version_revision BIGINT NOT NULL,
    embedding_deployment_id BIGINT NOT NULL,
    embedding_deployment_revision BIGINT NOT NULL,
    system_instruction VARCHAR(32000) NOT NULL,
    max_output_tokens INTEGER NOT NULL,
    retrieval_limit INTEGER NOT NULL,
    minimum_score DOUBLE PRECISION NOT NULL,
    citation_policy VARCHAR(32) NOT NULL,
    refusal_policy VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT pk_assistant_version PRIMARY KEY (tenant_id, id),
    CONSTRAINT uk_assistant_version_number
        UNIQUE (tenant_id, assistant_id, version_number),
    CONSTRAINT fk_assistant_version_assistant
        FOREIGN KEY (tenant_id, assistant_id)
        REFERENCES assistant_delivery.assistant (tenant_id, id),
    CONSTRAINT fk_assistant_version_chat_deployment
        FOREIGN KEY (tenant_id, chat_deployment_id)
        REFERENCES model_runtime.model_deployment (tenant_id, id),
    CONSTRAINT fk_assistant_version_index
        FOREIGN KEY (tenant_id, knowledge_space_id, index_version_id)
        REFERENCES knowledge_data.index_version (tenant_id, knowledge_space_id, id),
    CONSTRAINT ck_assistant_version_number CHECK (version_number > 0),
    CONSTRAINT ck_assistant_version_chat_revision
        CHECK (chat_deployment_revision > 0),
    CONSTRAINT ck_assistant_version_chat_limit
        CHECK (chat_deployment_output_limit > 0),
    CONSTRAINT ck_assistant_version_index_revision
        CHECK (index_version_revision > 0),
    CONSTRAINT ck_assistant_version_embedding_revision
        CHECK (embedding_deployment_revision > 0),
    CONSTRAINT ck_assistant_version_output_limit
        CHECK (
            max_output_tokens > 0
            AND max_output_tokens <= chat_deployment_output_limit
        ),
    CONSTRAINT ck_assistant_version_retrieval_limit
        CHECK (retrieval_limit BETWEEN 1 AND 20),
    CONSTRAINT ck_assistant_version_minimum_score
        CHECK (minimum_score BETWEEN 0.0 AND 1.0),
    CONSTRAINT ck_assistant_version_citation
        CHECK (citation_policy = 'REQUIRED'),
    CONSTRAINT ck_assistant_version_refusal
        CHECK (refusal_policy = 'INSUFFICIENT_EVIDENCE'),
    CONSTRAINT ck_assistant_version_status
        CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_assistant_version_revision CHECK (version >= 0),
    CONSTRAINT ck_assistant_version_state
        CHECK (
            (status = 'DRAFT' AND version = 0 AND published_at IS NULL)
            OR
            (status = 'PUBLISHED' AND version = 1 AND published_at IS NOT NULL)
        )
);

CREATE INDEX idx_assistant_version_assistant_created
    ON assistant_delivery.assistant_version (
        tenant_id,
        assistant_id,
        version_number DESC
    );

CREATE TABLE assistant_delivery.assistant_request (
    tenant_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    assistant_id BIGINT NOT NULL,
    assistant_version_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_assistant_request
        PRIMARY KEY (tenant_id, operation, idempotency_key_hash),
    CONSTRAINT fk_assistant_request_assistant
        FOREIGN KEY (tenant_id, assistant_id)
        REFERENCES assistant_delivery.assistant (tenant_id, id),
    CONSTRAINT fk_assistant_request_version
        FOREIGN KEY (tenant_id, assistant_version_id)
        REFERENCES assistant_delivery.assistant_version (tenant_id, id),
    CONSTRAINT ck_assistant_request_operation
        CHECK (operation IN ('CREATE_ASSISTANT', 'CREATE_VERSION')),
    CONSTRAINT ck_assistant_request_result
        CHECK (
            (operation = 'CREATE_ASSISTANT' AND assistant_version_id IS NULL)
            OR
            (operation = 'CREATE_VERSION' AND assistant_version_id IS NOT NULL)
        )
);
