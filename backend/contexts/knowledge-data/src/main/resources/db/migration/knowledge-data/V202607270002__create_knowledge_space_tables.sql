CREATE SCHEMA IF NOT EXISTS knowledge_data;

CREATE TABLE knowledge_data.knowledge_space (
    tenant_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    name VARCHAR(320) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_knowledge_space PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_knowledge_space_status
        CHECK (status IN ('DRAFT', 'DELETED')),
    CONSTRAINT ck_knowledge_space_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_knowledge_space_tenant_name
    ON knowledge_data.knowledge_space (tenant_id, lower(name));

CREATE INDEX idx_knowledge_space_tenant_created
    ON knowledge_data.knowledge_space (tenant_id, created_at DESC, id DESC);

CREATE TABLE knowledge_data.knowledge_space_request (
    tenant_id BIGINT NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    knowledge_space_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_knowledge_space_request
        PRIMARY KEY (tenant_id, idempotency_key_hash),
    CONSTRAINT fk_knowledge_space_request_space
        FOREIGN KEY (tenant_id, knowledge_space_id)
        REFERENCES knowledge_data.knowledge_space (tenant_id, id)
);
