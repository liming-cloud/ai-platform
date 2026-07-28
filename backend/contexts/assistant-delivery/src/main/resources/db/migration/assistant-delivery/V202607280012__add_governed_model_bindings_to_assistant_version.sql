-- 所属上下文：assistant-delivery。目的：为新助手版本冻结受控模型配置，历史 M1 部署绑定保持可读；可回滚性：仅新增列和约束，回滚需先移除受控版本数据。
ALTER TABLE assistant_delivery.assistant_version
    ALTER COLUMN chat_deployment_id DROP NOT NULL,
    ALTER COLUMN chat_deployment_revision DROP NOT NULL,
    ALTER COLUMN embedding_deployment_id DROP NOT NULL,
    ALTER COLUMN embedding_deployment_revision DROP NOT NULL,
    ADD COLUMN chat_model_configuration_id BIGINT,
    ADD COLUMN chat_model_configuration_revision BIGINT,
    ADD COLUMN chat_catalog_entry_id BIGINT,
    ADD COLUMN chat_catalog_entry_revision BIGINT,
    ADD COLUMN embedding_model_configuration_id BIGINT,
    ADD COLUMN embedding_model_configuration_revision BIGINT,
    ADD COLUMN embedding_catalog_entry_id BIGINT,
    ADD COLUMN embedding_catalog_entry_revision BIGINT,
    ADD CONSTRAINT ck_assistant_version_model_binding_kind CHECK (
        (
            chat_deployment_id IS NOT NULL
            AND chat_deployment_revision IS NOT NULL
            AND embedding_deployment_id IS NOT NULL
            AND embedding_deployment_revision IS NOT NULL
            AND chat_model_configuration_id IS NULL
            AND chat_model_configuration_revision IS NULL
            AND chat_catalog_entry_id IS NULL
            AND chat_catalog_entry_revision IS NULL
            AND embedding_model_configuration_id IS NULL
            AND embedding_model_configuration_revision IS NULL
            AND embedding_catalog_entry_id IS NULL
            AND embedding_catalog_entry_revision IS NULL
        )
        OR
        (
            chat_deployment_id IS NULL
            AND chat_deployment_revision IS NULL
            AND embedding_deployment_id IS NULL
            AND embedding_deployment_revision IS NULL
            AND chat_model_configuration_id IS NOT NULL
            AND chat_model_configuration_revision IS NOT NULL
            AND chat_catalog_entry_id IS NOT NULL
            AND chat_catalog_entry_revision IS NOT NULL
            AND embedding_model_configuration_id IS NOT NULL
            AND embedding_model_configuration_revision IS NOT NULL
            AND embedding_catalog_entry_id IS NOT NULL
            AND embedding_catalog_entry_revision IS NOT NULL
        )
    ),
    ADD CONSTRAINT ck_assistant_version_governed_model_binding_positive CHECK (
        (
            chat_model_configuration_id IS NULL
            AND chat_model_configuration_revision IS NULL
            AND chat_catalog_entry_id IS NULL
            AND chat_catalog_entry_revision IS NULL
            AND embedding_model_configuration_id IS NULL
            AND embedding_model_configuration_revision IS NULL
            AND embedding_catalog_entry_id IS NULL
            AND embedding_catalog_entry_revision IS NULL
        )
        OR
        (
            chat_model_configuration_id > 0
            AND chat_model_configuration_revision > 0
            AND chat_catalog_entry_id > 0
            AND chat_catalog_entry_revision > 0
            AND embedding_model_configuration_id > 0
            AND embedding_model_configuration_revision > 0
            AND embedding_catalog_entry_id > 0
            AND embedding_catalog_entry_revision > 0
        )
    );
