-- 所属上下文：knowledge-data。目的：让索引向量产物与索引版本使用同一份部署或受控配置绑定。不可回滚：受控向量写入后不能无损还原为仅部署绑定。
ALTER TABLE knowledge_data.index_chunk
    ALTER COLUMN embedding_deployment_id DROP NOT NULL,
    ALTER COLUMN embedding_deployment_revision DROP NOT NULL,
    ADD COLUMN embedding_configuration_id BIGINT,
    ADD COLUMN embedding_configuration_revision BIGINT,
    ADD COLUMN embedding_catalog_entry_id BIGINT,
    ADD COLUMN embedding_catalog_entry_revision BIGINT;

ALTER TABLE knowledge_data.index_chunk
    DROP CONSTRAINT ck_index_chunk_embedding,
    ADD CONSTRAINT ck_index_chunk_embedding
        CHECK (
            embedding_dimension BETWEEN 1 AND 2000
            AND vector_dims(embedding) = embedding_dimension
            AND (
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
            )
        );

DROP INDEX knowledge_data.idx_index_chunk_exact_filter;
CREATE INDEX idx_index_chunk_exact_filter
    ON knowledge_data.index_chunk (
        tenant_id,
        knowledge_space_id,
        index_version_id,
        embedding_deployment_id,
        embedding_deployment_revision,
        embedding_configuration_id,
        embedding_configuration_revision,
        embedding_dimension
    );
