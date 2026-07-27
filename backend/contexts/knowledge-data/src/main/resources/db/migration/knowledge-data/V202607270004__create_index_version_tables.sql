CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_data.index_version (
    tenant_id UUID NOT NULL,
    knowledge_space_id UUID NOT NULL,
    id UUID NOT NULL,
    source_snapshot_digest CHAR(64) NOT NULL,
    chunk_policy VARCHAR(64) NOT NULL,
    embedding_deployment_id UUID NOT NULL,
    embedding_deployment_revision BIGINT NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    build_digest CHAR(64),
    chunk_count INTEGER NOT NULL,
    failure_code VARCHAR(96),
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    ready_at TIMESTAMPTZ,
    activated_at TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,
    CONSTRAINT pk_index_version
        PRIMARY KEY (tenant_id, knowledge_space_id, id),
    CONSTRAINT fk_index_version_space
        FOREIGN KEY (tenant_id, knowledge_space_id)
        REFERENCES knowledge_data.knowledge_space (tenant_id, id),
    CONSTRAINT ck_index_version_source_digest
        CHECK (source_snapshot_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_index_version_chunk_policy
        CHECK (chunk_policy ~ '^[a-z][a-z0-9-]{2,63}$'),
    CONSTRAINT ck_index_version_embedding_revision
        CHECK (embedding_deployment_revision > 0),
    CONSTRAINT ck_index_version_embedding_dimension
        CHECK (embedding_dimension BETWEEN 1 AND 2000),
    CONSTRAINT ck_index_version_status
        CHECK (status IN ('BUILDING', 'READY', 'ACTIVE', 'SUPERSEDED', 'FAILED')),
    CONSTRAINT ck_index_version_version
        CHECK (version >= 0),
    CONSTRAINT ck_index_version_chunk_count
        CHECK (chunk_count >= 0),
    CONSTRAINT ck_index_version_state
        CHECK (
            (
                status = 'BUILDING'
                AND version = 0
                AND build_digest IS NULL
                AND chunk_count = 0
                AND failure_code IS NULL
                AND ready_at IS NULL
                AND activated_at IS NULL
                AND superseded_at IS NULL
            )
            OR (
                status = 'READY'
                AND build_digest ~ '^[0-9a-f]{64}$'
                AND chunk_count > 0
                AND failure_code IS NULL
                AND ready_at IS NOT NULL
                AND activated_at IS NULL
                AND superseded_at IS NULL
            )
            OR (
                status = 'ACTIVE'
                AND build_digest ~ '^[0-9a-f]{64}$'
                AND chunk_count > 0
                AND failure_code IS NULL
                AND ready_at IS NOT NULL
                AND activated_at IS NOT NULL
                AND superseded_at IS NULL
            )
            OR (
                status = 'SUPERSEDED'
                AND build_digest ~ '^[0-9a-f]{64}$'
                AND chunk_count > 0
                AND failure_code IS NULL
                AND ready_at IS NOT NULL
                AND activated_at IS NOT NULL
                AND superseded_at IS NOT NULL
            )
            OR (
                status = 'FAILED'
                AND build_digest IS NULL
                AND chunk_count = 0
                AND failure_code ~ '^[A-Z][A-Z0-9_]{2,95}$'
                AND ready_at IS NULL
                AND activated_at IS NULL
                AND superseded_at IS NULL
            )
        )
);

CREATE UNIQUE INDEX uk_index_version_active_space
    ON knowledge_data.index_version (tenant_id, knowledge_space_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_index_version_space_created
    ON knowledge_data.index_version (
        tenant_id,
        knowledge_space_id,
        created_at DESC,
        id DESC
    );

CREATE TABLE knowledge_data.index_version_source (
    tenant_id UUID NOT NULL,
    knowledge_space_id UUID NOT NULL,
    index_version_id UUID NOT NULL,
    data_source_id UUID NOT NULL,
    content_version INTEGER NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    media_type VARCHAR(64) NOT NULL,
    content_created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_index_version_source
        PRIMARY KEY (
            tenant_id,
            knowledge_space_id,
            index_version_id,
            data_source_id
        ),
    CONSTRAINT fk_index_version_source_version
        FOREIGN KEY (tenant_id, knowledge_space_id, index_version_id)
        REFERENCES knowledge_data.index_version (
            tenant_id,
            knowledge_space_id,
            id
        ),
    CONSTRAINT fk_index_version_source_content
        FOREIGN KEY (
            tenant_id,
            knowledge_space_id,
            data_source_id,
            content_version
        )
        REFERENCES knowledge_data.text_data_source_content (
            tenant_id,
            knowledge_space_id,
            data_source_id,
            version
        ),
    CONSTRAINT ck_index_version_source_content_version
        CHECK (content_version > 0),
    CONSTRAINT ck_index_version_source_sha
        CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_index_version_source_size
        CHECK (byte_size > 0 AND byte_size <= 20971520),
    CONSTRAINT ck_index_version_source_media_type
        CHECK (media_type = 'text/plain; charset=UTF-8')
);

CREATE TABLE knowledge_data.index_build_job (
    tenant_id UUID NOT NULL,
    knowledge_space_id UUID NOT NULL,
    index_version_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt INTEGER NOT NULL,
    lease_id UUID,
    lease_until TIMESTAMPTZ,
    failure_code VARCHAR(96),
    available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT pk_index_build_job
        PRIMARY KEY (tenant_id, knowledge_space_id, index_version_id),
    CONSTRAINT fk_index_build_job_version
        FOREIGN KEY (tenant_id, knowledge_space_id, index_version_id)
        REFERENCES knowledge_data.index_version (
            tenant_id,
            knowledge_space_id,
            id
        ),
    CONSTRAINT ck_index_build_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT ck_index_build_job_attempt
        CHECK (attempt >= 0),
    CONSTRAINT ck_index_build_job_state
        CHECK (
            (status = 'PENDING' AND lease_id IS NULL AND lease_until IS NULL)
            OR (status = 'RUNNING' AND lease_id IS NOT NULL AND lease_until IS NOT NULL)
            OR (
                status = 'DONE'
                AND lease_id IS NULL
                AND lease_until IS NULL
                AND failure_code IS NULL
                AND completed_at IS NOT NULL
            )
            OR (
                status = 'FAILED'
                AND lease_id IS NULL
                AND lease_until IS NULL
                AND failure_code ~ '^[A-Z][A-Z0-9_]{2,95}$'
                AND completed_at IS NOT NULL
            )
        )
);

CREATE INDEX idx_index_build_job_claim
    ON knowledge_data.index_build_job (available_at, created_at, index_version_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE knowledge_data.index_version_request (
    tenant_id UUID NOT NULL,
    operation VARCHAR(24) NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    knowledge_space_id UUID NOT NULL,
    index_version_id UUID NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    result_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_index_version_request
        PRIMARY KEY (tenant_id, operation, idempotency_key_hash),
    CONSTRAINT fk_index_version_request_version
        FOREIGN KEY (tenant_id, knowledge_space_id, index_version_id)
        REFERENCES knowledge_data.index_version (
            tenant_id,
            knowledge_space_id,
            id
        ),
    CONSTRAINT ck_index_version_request_operation
        CHECK (operation IN ('CREATE', 'ACTIVATE')),
    CONSTRAINT ck_index_version_request_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_index_version_request_result
        CHECK (
            result_status IN ('BUILDING', 'ACTIVE')
            AND result_version >= 0
        )
);

CREATE TABLE knowledge_data.index_chunk (
    tenant_id UUID NOT NULL,
    knowledge_space_id UUID NOT NULL,
    index_version_id UUID NOT NULL,
    data_source_id UUID NOT NULL,
    content_version INTEGER NOT NULL,
    chunk_id CHAR(64) NOT NULL,
    ordinal INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    embedding_deployment_id UUID NOT NULL,
    embedding_deployment_revision BIGINT NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    embedding vector NOT NULL,
    CONSTRAINT pk_index_chunk
        PRIMARY KEY (
            tenant_id,
            knowledge_space_id,
            index_version_id,
            chunk_id
        ),
    CONSTRAINT fk_index_chunk_version
        FOREIGN KEY (tenant_id, knowledge_space_id, index_version_id)
        REFERENCES knowledge_data.index_version (
            tenant_id,
            knowledge_space_id,
            id
        ),
    CONSTRAINT ck_index_chunk_content_version
        CHECK (content_version > 0),
    CONSTRAINT ck_index_chunk_ordinal
        CHECK (ordinal >= 0),
    CONSTRAINT ck_index_chunk_content
        CHECK (length(content) > 0),
    CONSTRAINT ck_index_chunk_sha
        CHECK (
            chunk_id ~ '^[0-9a-f]{64}$'
            AND content_sha256 ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT ck_index_chunk_embedding
        CHECK (
            embedding_deployment_revision > 0
            AND embedding_dimension BETWEEN 1 AND 2000
            AND vector_dims(embedding) = embedding_dimension
        )
);

CREATE INDEX idx_index_chunk_exact_filter
    ON knowledge_data.index_chunk (
        tenant_id,
        knowledge_space_id,
        index_version_id,
        embedding_deployment_id,
        embedding_deployment_revision,
        embedding_dimension
    );
