ALTER TABLE assistant_delivery.assistant_version
    ADD COLUMN embedding_dimension INTEGER;

UPDATE assistant_delivery.assistant_version version
SET embedding_dimension = index_version.embedding_dimension
FROM knowledge_data.index_version index_version
WHERE index_version.tenant_id = version.tenant_id
  AND index_version.knowledge_space_id = version.knowledge_space_id
  AND index_version.id = version.index_version_id;

ALTER TABLE assistant_delivery.assistant_version
    ALTER COLUMN embedding_dimension SET NOT NULL,
    ADD CONSTRAINT ck_assistant_version_embedding_dimension
        CHECK (embedding_dimension > 0);
