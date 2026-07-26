CREATE TABLE model_runtime.model_deployment (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    provider_connection_id UUID NOT NULL,
    provider_connection_revision BIGINT NOT NULL,
    model_code VARCHAR(320) NOT NULL,
    display_name VARCHAR(320) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    capabilities TEXT[] NOT NULL,
    context_window INTEGER NOT NULL,
    output_limit INTEGER,
    embedding_dimension INTEGER,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_model_deployment PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_model_deployment_provider
        FOREIGN KEY (tenant_id, provider_connection_id)
        REFERENCES model_runtime.provider_connection (tenant_id, id),
    CONSTRAINT ck_model_deployment_provider_revision
        CHECK (provider_connection_revision >= 0),
    CONSTRAINT ck_model_deployment_type
        CHECK (model_type IN ('CHAT', 'EMBEDDING')),
    CONSTRAINT ck_model_deployment_capabilities
        CHECK (cardinality(capabilities) > 0),
    CONSTRAINT ck_model_deployment_context_window
        CHECK (context_window > 0),
    CONSTRAINT ck_model_deployment_type_configuration
        CHECK (
            (
                model_type = 'CHAT'
                AND output_limit > 0
                AND embedding_dimension IS NULL
                AND capabilities @> ARRAY['CHAT']::TEXT[]
            )
            OR
            (
                model_type = 'EMBEDDING'
                AND embedding_dimension > 0
                AND output_limit IS NULL
                AND capabilities @> ARRAY['EMBEDDING']::TEXT[]
            )
        ),
    CONSTRAINT ck_model_deployment_status
        CHECK (status IN ('DRAFT', 'VERIFIED', 'PUBLISHED')),
    CONSTRAINT ck_model_deployment_version
        CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_model_deployment_tenant_name
    ON model_runtime.model_deployment (tenant_id, lower(display_name));

CREATE INDEX idx_model_deployment_tenant_created
    ON model_runtime.model_deployment (tenant_id, created_at DESC, id DESC);

CREATE INDEX idx_model_deployment_provider_revision
    ON model_runtime.model_deployment (
        tenant_id,
        provider_connection_id,
        provider_connection_revision
    );

CREATE TABLE model_runtime.model_deployment_request (
    tenant_id UUID NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    model_deployment_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_model_deployment_request
        PRIMARY KEY (tenant_id, idempotency_key_hash),
    CONSTRAINT fk_model_deployment_request_deployment
        FOREIGN KEY (tenant_id, model_deployment_id)
        REFERENCES model_runtime.model_deployment (tenant_id, id)
);
