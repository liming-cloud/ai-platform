package com.aiplatform.appstudio.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * AI 应用模型使用策略本地投影持久化实体。
 */
@TableName("app_model_usage_policy_projection")
public class AppModelUsagePolicyProjectionPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("model_config_id")
    private Long modelConfigId;
    @TableField("provider_id")
    private Long providerId;
    @TableField("provider_code")
    private String providerCode;
    @TableField("provider_kind")
    private String providerKind;
    @TableField("provider_endpoint")
    private String providerEndpoint;
    @TableField("auth_type")
    private String authType;
    @TableField("secret_ref_id")
    private String secretRefId;
    @TableField("model_code")
    private String modelCode;
    @TableField("model_config_version")
    private Integer modelConfigVersion;
    @TableField("model_type")
    private String modelType;
    @TableField("capabilities")
    private String capabilities;
    @TableField("allowed_use_cases")
    private String allowedUseCases;
    @TableField("allowed_application_ids")
    private String allowedApplicationIds;
    @TableField("effective_options_json")
    private String effectiveOptionsJson;
    @TableField("effective_options_digest")
    private String effectiveOptionsDigest;
    @TableField("max_input_tokens")
    private Integer maxInputTokens;
    @TableField("max_output_tokens")
    private Integer maxOutputTokens;
    @TableField("policy_version")
    private String policyVersion;
    @TableField("policy_revision")
    private Long policyRevision;
    @TableField("policy_digest")
    private String policyDigest;
    @TableField("expires_at")
    private Instant expiresAt;
    @TableField("enabled")
    private Boolean enabled;
    @TableField("source_event_id")
    private String sourceEventId;
    @TableField("source_event_version")
    private Long sourceEventVersion;
    @TableField("last_event_at")
    private Instant lastEventAt;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;
    @TableField("version")
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getModelConfigId() { return modelConfigId; }
    public void setModelConfigId(Long modelConfigId) { this.modelConfigId = modelConfigId; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getProviderKind() { return providerKind; }
    public void setProviderKind(String providerKind) { this.providerKind = providerKind; }
    public String getProviderEndpoint() { return providerEndpoint; }
    public void setProviderEndpoint(String providerEndpoint) { this.providerEndpoint = providerEndpoint; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getSecretRefId() { return secretRefId; }
    public void setSecretRefId(String secretRefId) { this.secretRefId = secretRefId; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public Integer getModelConfigVersion() { return modelConfigVersion; }
    public void setModelConfigVersion(Integer modelConfigVersion) { this.modelConfigVersion = modelConfigVersion; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public String getCapabilities() { return capabilities; }
    public void setCapabilities(String capabilities) { this.capabilities = capabilities; }
    public String getAllowedUseCases() { return allowedUseCases; }
    public void setAllowedUseCases(String allowedUseCases) { this.allowedUseCases = allowedUseCases; }
    public String getAllowedApplicationIds() { return allowedApplicationIds; }
    public void setAllowedApplicationIds(String allowedApplicationIds) { this.allowedApplicationIds = allowedApplicationIds; }
    public String getEffectiveOptionsJson() { return effectiveOptionsJson; }
    public void setEffectiveOptionsJson(String effectiveOptionsJson) { this.effectiveOptionsJson = effectiveOptionsJson; }
    public String getEffectiveOptionsDigest() { return effectiveOptionsDigest; }
    public void setEffectiveOptionsDigest(String effectiveOptionsDigest) { this.effectiveOptionsDigest = effectiveOptionsDigest; }
    public Integer getMaxInputTokens() { return maxInputTokens; }
    public void setMaxInputTokens(Integer maxInputTokens) { this.maxInputTokens = maxInputTokens; }
    public Integer getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(Integer maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public Long getPolicyRevision() { return policyRevision; }
    public void setPolicyRevision(Long policyRevision) { this.policyRevision = policyRevision; }
    public String getPolicyDigest() { return policyDigest; }
    public void setPolicyDigest(String policyDigest) { this.policyDigest = policyDigest; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }
    public Long getSourceEventVersion() { return sourceEventVersion; }
    public void setSourceEventVersion(Long sourceEventVersion) { this.sourceEventVersion = sourceEventVersion; }
    public Instant getLastEventAt() { return lastEventAt; }
    public void setLastEventAt(Instant lastEventAt) { this.lastEventAt = lastEventAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
