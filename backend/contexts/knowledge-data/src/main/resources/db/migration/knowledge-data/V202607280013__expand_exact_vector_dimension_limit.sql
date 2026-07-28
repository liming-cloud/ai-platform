-- 所属上下文：knowledge-data。目的：支持 2048 维受控嵌入在当前 pgvector 精确检索中持久化。影响：既有索引向量不变，新向量可达 16000 维。回滚限制：存在超过 2000 维的数据后不得恢复旧约束。
ALTER TABLE knowledge_data.index_chunk
    DROP CONSTRAINT ck_index_chunk_embedding,
    ADD CONSTRAINT ck_index_chunk_embedding
        CHECK (
            embedding_dimension BETWEEN 1 AND 16000
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
