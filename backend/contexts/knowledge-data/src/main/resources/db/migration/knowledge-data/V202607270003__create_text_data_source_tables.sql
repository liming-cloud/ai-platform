CREATE TABLE knowledge_data.text_data_source (
    tenant_id BIGINT NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    name VARCHAR(320) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    content_version INTEGER NOT NULL,
    published_content_version INTEGER NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_text_data_source
        PRIMARY KEY (tenant_id, knowledge_space_id, id),
    CONSTRAINT fk_text_data_source_space
        FOREIGN KEY (tenant_id, knowledge_space_id)
        REFERENCES knowledge_data.knowledge_space (tenant_id, id),
    CONSTRAINT ck_text_data_source_status
        CHECK (status IN ('DRAFT', 'CONTENT_READY', 'PUBLISHED')),
    CONSTRAINT ck_text_data_source_version
        CHECK (version >= 0),
    CONSTRAINT ck_text_data_source_content_version
        CHECK (
            content_version >= 0
            AND published_content_version >= 0
            AND published_content_version <= content_version
        ),
    CONSTRAINT ck_text_data_source_state
        CHECK (
            (status = 'DRAFT' AND content_version = 0)
            OR (
                status = 'CONTENT_READY'
                AND content_version > 0
                AND published_content_version < content_version
            )
            OR (
                status = 'PUBLISHED'
                AND content_version > 0
                AND published_content_version = content_version
            )
        )
);

CREATE UNIQUE INDEX uk_text_data_source_space_name
    ON knowledge_data.text_data_source (
        tenant_id,
        knowledge_space_id,
        lower(name)
    );

CREATE INDEX idx_text_data_source_tenant_created
    ON knowledge_data.text_data_source (tenant_id, created_at DESC, id DESC);

CREATE TABLE knowledge_data.text_data_source_content (
    tenant_id BIGINT NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    media_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_text_data_source_content
        PRIMARY KEY (
            tenant_id,
            knowledge_space_id,
            data_source_id,
            version
        ),
    CONSTRAINT fk_text_data_source_content_source
        FOREIGN KEY (tenant_id, knowledge_space_id, data_source_id)
        REFERENCES knowledge_data.text_data_source (
            tenant_id,
            knowledge_space_id,
            id
        ),
    CONSTRAINT ck_text_data_source_content_version
        CHECK (version > 0),
    CONSTRAINT ck_text_data_source_content_sha
        CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_text_data_source_content_size
        CHECK (byte_size > 0 AND byte_size <= 20971520),
    CONSTRAINT ck_text_data_source_content_media_type
        CHECK (media_type = 'text/plain; charset=UTF-8')
);

CREATE INDEX idx_text_data_source_content_object
    ON knowledge_data.text_data_source_content (tenant_id, object_key);

CREATE TABLE knowledge_data.text_data_source_request (
    tenant_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    result_version BIGINT NOT NULL,
    result_content_version INTEGER NOT NULL,
    result_published_content_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_text_data_source_request
        PRIMARY KEY (tenant_id, operation, idempotency_key_hash),
    CONSTRAINT fk_text_data_source_request_source
        FOREIGN KEY (tenant_id, knowledge_space_id, data_source_id)
        REFERENCES knowledge_data.text_data_source (
            tenant_id,
            knowledge_space_id,
            id
        ),
    CONSTRAINT ck_text_data_source_request_operation
        CHECK (operation IN ('CREATE', 'ATTACH_CONTENT', 'PUBLISH')),
    CONSTRAINT ck_text_data_source_request_status
        CHECK (result_status IN ('DRAFT', 'CONTENT_READY', 'PUBLISHED')),
    CONSTRAINT ck_text_data_source_request_versions
        CHECK (
            result_version >= 0
            AND result_content_version >= 0
            AND result_published_content_version >= 0
            AND result_published_content_version <= result_content_version
        )
);

CREATE TABLE knowledge_data.outbox_event (
    tenant_id BIGINT NOT NULL,
    event_id UUID NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT pk_knowledge_data_outbox_event
        PRIMARY KEY (tenant_id, event_id)
);

CREATE INDEX idx_knowledge_data_outbox_pending
    ON knowledge_data.outbox_event (occurred_at, event_id)
    WHERE published_at IS NULL;
