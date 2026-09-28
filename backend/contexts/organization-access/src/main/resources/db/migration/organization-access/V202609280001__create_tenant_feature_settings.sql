-- 所属上下文：organization-access。依据 ADR-0085，为八项租户功能建立独立版本事实。
-- 现有租户显式回填全开以保持已交付能力；后续原子开户必须同步写入，缺行不得默认放行。
-- 删除本表会丢失已保存的功能限制，不能作为无损自动回滚。
CREATE TABLE organization_access.tenant_feature_settings (
    tenant_id BIGINT PRIMARY KEY REFERENCES organization_access.tenant (tenant_id) ON DELETE CASCADE,
    knowledge_management BOOLEAN NOT NULL,
    model_governance BOOLEAN NOT NULL,
    assistant_delivery BOOLEAN NOT NULL,
    evaluation_governance BOOLEAN NOT NULL,
    assistant_selection BOOLEAN NOT NULL,
    conversation BOOLEAN NOT NULL,
    own_history BOOLEAN NOT NULL,
    authorized_citations BOOLEAN NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0)
);

INSERT INTO organization_access.tenant_feature_settings (
    tenant_id, knowledge_management, model_governance, assistant_delivery,
    evaluation_governance, assistant_selection, conversation, own_history,
    authorized_citations, version
)
SELECT tenant_id, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, 0
FROM organization_access.tenant;
