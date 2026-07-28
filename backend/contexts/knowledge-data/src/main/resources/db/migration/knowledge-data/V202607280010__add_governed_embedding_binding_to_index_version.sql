-- 所属上下文：knowledge-data。目的：允许新索引冻结受控嵌入配置快照；历史部署绑定保留只读事实。不可回滚：新受控索引写入后不能无损降级为旧部署绑定。
ALTER TABLE knowledge_data.index_version
    ALTER COLUMN embedding_deployment_id DROP NOT NULL,
    ALTER COLUMN embedding_deployment_revision DROP NOT NULL,
    ADD COLUMN embedding_configuration_id BIGINT,
    ADD COLUMN embedding_configuration_revision BIGINT,
    ADD COLUMN embedding_catalog_entry_id BIGINT,
    ADD COLUMN embedding_catalog_entry_revision BIGINT;

ALTER TABLE knowledge_data.index_version
    DROP CONSTRAINT ck_index_version_embedding_revision,
    ADD CONSTRAINT ck_index_version_embedding_binding
        CHECK (
            (
                embedding_deployment_id IS NOT NULL
                AND embedding_deployment_revision IS NOT NULL
                AND embedding_deployment_revision > 0
                AND embedding_configuration_id IS NULL
                AND embedding_configuration_revision IS NULL
                AND embedding_catalog_entry_id IS NULL
                AND embedding_catalog_entry_revision IS NULL
            )
            OR (
                embedding_deployment_id IS NULL
                AND embedding_deployment_revision IS NULL
                AND embedding_configuration_id IS NOT NULL
                AND embedding_configuration_revision > 0
                AND embedding_catalog_entry_id IS NOT NULL
                AND embedding_catalog_entry_revision > 0
            )
        );
