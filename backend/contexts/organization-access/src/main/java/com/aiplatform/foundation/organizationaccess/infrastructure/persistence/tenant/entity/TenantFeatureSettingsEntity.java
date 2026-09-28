package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.EnumMap;
import java.util.Objects;

/** 可变持久化字段只留在基础设施；恢复时由领域快照重新校验完整性。 */
@TableName("organization_access.tenant_feature_settings")
public class TenantFeatureSettingsEntity {
    @TableId(type = IdType.INPUT)
    private Long tenantId;
    private Boolean knowledgeManagement;
    private Boolean modelGovernance;
    private Boolean assistantDelivery;
    private Boolean evaluationGovernance;
    private Boolean assistantSelection;
    private Boolean conversation;
    private Boolean ownHistory;
    private Boolean authorizedCitations;
    private Long version;

    public static TenantFeatureSettingsEntity from(TenantFeatureSettings settings) {
        Objects.requireNonNull(settings, "功能配置不能为空");
        var row = new TenantFeatureSettingsEntity();
        row.tenantId = settings.tenantId().value();
        row.knowledgeManagement = settings.allows(TenantFeature.KNOWLEDGE_MANAGEMENT);
        row.modelGovernance = settings.allows(TenantFeature.MODEL_GOVERNANCE);
        row.assistantDelivery = settings.allows(TenantFeature.ASSISTANT_DELIVERY);
        row.evaluationGovernance = settings.allows(TenantFeature.EVALUATION_GOVERNANCE);
        row.assistantSelection = settings.allows(TenantFeature.ASSISTANT_SELECTION);
        row.conversation = settings.allows(TenantFeature.CONVERSATION);
        row.ownHistory = settings.allows(TenantFeature.OWN_HISTORY);
        row.authorizedCitations = settings.allows(TenantFeature.AUTHORIZED_CITATIONS);
        row.version = settings.version();
        return row;
    }

    public TenantFeatureSettings toDomain() {
        var values = new EnumMap<TenantFeature, Boolean>(TenantFeature.class);
        values.put(TenantFeature.KNOWLEDGE_MANAGEMENT, knowledgeManagement);
        values.put(TenantFeature.MODEL_GOVERNANCE, modelGovernance);
        values.put(TenantFeature.ASSISTANT_DELIVERY, assistantDelivery);
        values.put(TenantFeature.EVALUATION_GOVERNANCE, evaluationGovernance);
        values.put(TenantFeature.ASSISTANT_SELECTION, assistantSelection);
        values.put(TenantFeature.CONVERSATION, conversation);
        values.put(TenantFeature.OWN_HISTORY, ownHistory);
        values.put(TenantFeature.AUTHORIZED_CITATIONS, authorizedCitations);
        return new TenantFeatureSettings(new TenantId(tenantId), values, version);
    }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Boolean getKnowledgeManagement() { return knowledgeManagement; }
    public void setKnowledgeManagement(Boolean value) { knowledgeManagement = value; }
    public Boolean getModelGovernance() { return modelGovernance; }
    public void setModelGovernance(Boolean value) { modelGovernance = value; }
    public Boolean getAssistantDelivery() { return assistantDelivery; }
    public void setAssistantDelivery(Boolean value) { assistantDelivery = value; }
    public Boolean getEvaluationGovernance() { return evaluationGovernance; }
    public void setEvaluationGovernance(Boolean value) { evaluationGovernance = value; }
    public Boolean getAssistantSelection() { return assistantSelection; }
    public void setAssistantSelection(Boolean value) { assistantSelection = value; }
    public Boolean getConversation() { return conversation; }
    public void setConversation(Boolean value) { conversation = value; }
    public Boolean getOwnHistory() { return ownHistory; }
    public void setOwnHistory(Boolean value) { ownHistory = value; }
    public Boolean getAuthorizedCitations() { return authorizedCitations; }
    public void setAuthorizedCitations(Boolean value) { authorizedCitations = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
