CREATE TABLE IF NOT EXISTS iam_role (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    role_code VARCHAR(96) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iam_role_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT uk_iam_role_tenant_code UNIQUE (tenant_id, role_code)
);

CREATE INDEX IF NOT EXISTS idx_iam_role_tenant ON iam_role (tenant_id);

CREATE TABLE IF NOT EXISTS iam_permission (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iam_permission_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT uk_iam_permission_tenant_code UNIQUE (tenant_id, permission_code)
);

CREATE INDEX IF NOT EXISTS idx_iam_permission_tenant ON iam_permission (tenant_id);

CREATE TABLE IF NOT EXISTS iam_menu (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    menu_code VARCHAR(128) NOT NULL,
    menu_name VARCHAR(128) NOT NULL,
    parent_code VARCHAR(128),
    route_path VARCHAR(256) NOT NULL,
    component_key VARCHAR(128) NOT NULL,
    platform_scope VARCHAR(32) NOT NULL,
    sort_order INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iam_menu_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT uk_iam_menu_tenant_code UNIQUE (tenant_id, menu_code)
);

CREATE INDEX IF NOT EXISTS idx_iam_menu_tenant_sort ON iam_menu (tenant_id, sort_order);

CREATE TABLE IF NOT EXISTS iam_user_role (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iam_user_role_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT fk_iam_user_role_user FOREIGN KEY (user_id) REFERENCES iam_user (id),
    CONSTRAINT fk_iam_user_role_role FOREIGN KEY (role_id) REFERENCES iam_role (id),
    CONSTRAINT uk_iam_user_role UNIQUE (tenant_id, user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_iam_user_role_user ON iam_user_role (tenant_id, user_id);

CREATE TABLE IF NOT EXISTS iam_role_permission (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iam_role_permission_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT fk_iam_role_permission_role FOREIGN KEY (role_id) REFERENCES iam_role (id),
    CONSTRAINT fk_iam_role_permission_permission FOREIGN KEY (permission_id) REFERENCES iam_permission (id),
    CONSTRAINT uk_iam_role_permission UNIQUE (tenant_id, role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_iam_role_permission_role ON iam_role_permission (tenant_id, role_id);

CREATE TABLE IF NOT EXISTS iam_role_menu (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iam_role_menu_tenant FOREIGN KEY (tenant_id) REFERENCES platform_tenant (id),
    CONSTRAINT fk_iam_role_menu_role FOREIGN KEY (role_id) REFERENCES iam_role (id),
    CONSTRAINT fk_iam_role_menu_menu FOREIGN KEY (menu_id) REFERENCES iam_menu (id),
    CONSTRAINT uk_iam_role_menu UNIQUE (tenant_id, role_id, menu_id)
);

CREATE INDEX IF NOT EXISTS idx_iam_role_menu_role ON iam_role_menu (tenant_id, role_id);

CREATE TABLE IF NOT EXISTS platform_provider_type (
    id BIGINT PRIMARY KEY,
    provider_type VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    default_base_url VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_model_option (
    id BIGINT PRIMARY KEY,
    provider_type_id BIGINT NOT NULL,
    model_code VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    capability_type VARCHAR(32) NOT NULL,
    context_window INTEGER,
    max_output_tokens INTEGER,
    embedding_dimensions INTEGER,
    default_temperature NUMERIC(6, 4),
    default_top_p NUMERIC(6, 4),
    default_top_k INTEGER,
    default_frequency_penalty NUMERIC(6, 4),
    default_presence_penalty NUMERIC(6, 4),
    default_reasoning_effort VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_model_option_provider_type FOREIGN KEY (provider_type_id) REFERENCES platform_provider_type (id),
    CONSTRAINT uk_platform_model_option UNIQUE (provider_type_id, model_code, capability_type)
);

CREATE INDEX IF NOT EXISTS idx_platform_model_option_provider_type ON platform_model_option (provider_type_id);
