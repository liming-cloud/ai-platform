-- 所属上下文：model-runtime。目的：ADR-0095 将连接发布与有期限探测、模型部署及租户授权分别保存。
-- 影响：只新增无秘密发布事实；历史 VERIFIED 连接不自动变为已发布。删除迁移会丢失发布与操作者审计，不能无损回滚。
CREATE TABLE model_runtime.platform_provider_connection_publication (
    id BIGINT PRIMARY KEY,
    connection_id BIGINT NOT NULL REFERENCES model_runtime.platform_provider_connection (id),
    revision BIGINT NOT NULL CHECK (revision > 0),
    connection_revision BIGINT NOT NULL CHECK (connection_revision >= 0),
    published_by_subject_id BIGINT NOT NULL CHECK (published_by_subject_id > 0),
    probed_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NOT NULL CHECK (published_at >= probed_at),
    UNIQUE (connection_id, revision),
    UNIQUE (connection_id, connection_revision)
);
