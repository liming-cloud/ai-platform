-- 所属上下文：assistant-delivery。
-- 目的：冻结助手版本的有序聊天模型候选，避免发布后路由随配置或应用层状态漂移。
-- 影响：新增受控聊天路由候选表；回滚前必须先删除所有受控助手版本的候选快照。
CREATE TABLE assistant_delivery.assistant_version_chat_route_candidate (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    assistant_version_id BIGINT NOT NULL,
    ordinal SMALLINT NOT NULL,
    configuration_id BIGINT NOT NULL,
    configuration_revision BIGINT NOT NULL,
    catalog_entry_id BIGINT NOT NULL,
    catalog_entry_revision BIGINT NOT NULL,
    output_limit INTEGER NOT NULL,
    CONSTRAINT uk_assistant_version_chat_route_candidate_ordinal
        UNIQUE (tenant_id, assistant_version_id, ordinal),
    CONSTRAINT fk_assistant_version_chat_route_candidate_version
        FOREIGN KEY (tenant_id, assistant_version_id)
        REFERENCES assistant_delivery.assistant_version (tenant_id, id),
    CONSTRAINT ck_assistant_version_chat_route_candidate_values CHECK (
        ordinal BETWEEN 1 AND 3
        AND configuration_id > 0
        AND configuration_revision > 0
        AND catalog_entry_id > 0
        AND catalog_entry_revision > 0
        AND output_limit > 0
    )
);
