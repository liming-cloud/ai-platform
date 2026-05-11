package org.fujay.ai.platform.model.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("model_catalog")
public class ModelCatalogPO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long providerId;
    private String modelCode;
    private String displayName;
    private String capabilityType;
    private String status;
    private Integer contextWindow;
    private Integer maxOutputTokens;
    private Integer embeddingDimensions;
    private BigDecimal defaultTemperature;
    private BigDecimal defaultTopP;
    private Integer defaultTopK;
    private BigDecimal defaultFrequencyPenalty;
    private BigDecimal defaultPresencePenalty;
    private String defaultReasoningEffort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCapabilityType() {
        return capabilityType;
    }

    public void setCapabilityType(String capabilityType) {
        this.capabilityType = capabilityType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(Integer contextWindow) {
        this.contextWindow = contextWindow;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public Integer getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(Integer embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public BigDecimal getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(BigDecimal defaultTemperature) {
        this.defaultTemperature = defaultTemperature;
    }

    public BigDecimal getDefaultTopP() {
        return defaultTopP;
    }

    public void setDefaultTopP(BigDecimal defaultTopP) {
        this.defaultTopP = defaultTopP;
    }

    public Integer getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(Integer defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public BigDecimal getDefaultFrequencyPenalty() {
        return defaultFrequencyPenalty;
    }

    public void setDefaultFrequencyPenalty(BigDecimal defaultFrequencyPenalty) {
        this.defaultFrequencyPenalty = defaultFrequencyPenalty;
    }

    public BigDecimal getDefaultPresencePenalty() {
        return defaultPresencePenalty;
    }

    public void setDefaultPresencePenalty(BigDecimal defaultPresencePenalty) {
        this.defaultPresencePenalty = defaultPresencePenalty;
    }

    public String getDefaultReasoningEffort() {
        return defaultReasoningEffort;
    }

    public void setDefaultReasoningEffort(String defaultReasoningEffort) {
        this.defaultReasoningEffort = defaultReasoningEffort;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
