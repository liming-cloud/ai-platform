-- 所属上下文：knowledge-data。目的：使索引版本冻结的嵌入维度与 16000 维精确向量存储上限一致。影响：既有索引版本不变，新版本可冻结 2048 维模型。回滚限制：存在超过 2000 维的索引版本后不得恢复旧约束。
ALTER TABLE knowledge_data.index_version
    DROP CONSTRAINT ck_index_version_embedding_dimension,
    ADD CONSTRAINT ck_index_version_embedding_dimension
        CHECK (embedding_dimension BETWEEN 1 AND 16000);
