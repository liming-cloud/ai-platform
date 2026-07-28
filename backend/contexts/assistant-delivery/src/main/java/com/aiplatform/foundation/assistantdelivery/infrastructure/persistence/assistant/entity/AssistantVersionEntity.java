package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * 助手不可变配置版本持久化实体。
 */
@TableName("assistant_delivery.assistant_version")
public final class AssistantVersionEntity {

    @TableField("tenant_id")
    private Long tenantId;
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("assistant_id")
    private Long assistantId;
    @TableField("version_number")
    private int versionNumber;
    @TableField("chat_deployment_id")
    private Long chatDeploymentId;
    @TableField("chat_deployment_revision")
    private Long chatDeploymentRevision;
    @TableField("chat_deployment_output_limit")
    private int chatDeploymentOutputLimit;
    @TableField("knowledge_space_id")
    private Long knowledgeSpaceId;
    @TableField("index_version_id")
    private Long indexVersionId;
    @TableField("index_version_revision")
    private long indexVersionRevision;
    @TableField("embedding_deployment_id")
    private Long embeddingDeploymentId;
    @TableField("embedding_deployment_revision")
    private Long embeddingDeploymentRevision;
    @TableField("embedding_dimension")
    private int embeddingDimension;
    @TableField("chat_model_configuration_id")
    private Long chatModelConfigurationId;
    @TableField("chat_model_configuration_revision")
    private Long chatModelConfigurationRevision;
    @TableField("chat_catalog_entry_id")
    private Long chatCatalogEntryId;
    @TableField("chat_catalog_entry_revision")
    private Long chatCatalogEntryRevision;
    @TableField("embedding_model_configuration_id")
    private Long embeddingModelConfigurationId;
    @TableField("embedding_model_configuration_revision")
    private Long embeddingModelConfigurationRevision;
    @TableField("embedding_catalog_entry_id")
    private Long embeddingCatalogEntryId;
    @TableField("embedding_catalog_entry_revision")
    private Long embeddingCatalogEntryRevision;
    @TableField("system_instruction")
    private String systemInstruction;
    @TableField("max_output_tokens")
    private int maxOutputTokens;
    @TableField("retrieval_limit")
    private int retrievalLimit;
    @TableField("minimum_score")
    private double minimumScore;
    @TableField("citation_policy")
    private String citationPolicy;
    @TableField("refusal_policy")
    private String refusalPolicy;
    @TableField("status")
    private String status;
    @Version
    @TableField("version")
    private long version;
    @TableField("created_by")
    private Long createdBy;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("published_at")
    private Instant publishedAt;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getAssistantId() { return assistantId; }
    public void setAssistantId(Long value) { assistantId = value; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int value) { versionNumber = value; }
    public Long getChatDeploymentId() { return chatDeploymentId; }
    public void setChatDeploymentId(Long value) { chatDeploymentId = value; }
    public Long getChatDeploymentRevision() { return chatDeploymentRevision; }
    public void setChatDeploymentRevision(Long value) { chatDeploymentRevision = value; }
    public int getChatDeploymentOutputLimit() { return chatDeploymentOutputLimit; }
    public void setChatDeploymentOutputLimit(int value) { chatDeploymentOutputLimit = value; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long value) { knowledgeSpaceId = value; }
    public Long getIndexVersionId() { return indexVersionId; }
    public void setIndexVersionId(Long value) { indexVersionId = value; }
    public long getIndexVersionRevision() { return indexVersionRevision; }
    public void setIndexVersionRevision(long value) { indexVersionRevision = value; }
    public Long getEmbeddingDeploymentId() { return embeddingDeploymentId; }
    public void setEmbeddingDeploymentId(Long value) { embeddingDeploymentId = value; }
    public Long getEmbeddingDeploymentRevision() { return embeddingDeploymentRevision; }
    public void setEmbeddingDeploymentRevision(Long value) { embeddingDeploymentRevision = value; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int value) { embeddingDimension = value; }
    public Long getChatModelConfigurationId() { return chatModelConfigurationId; }
    public void setChatModelConfigurationId(Long value) { chatModelConfigurationId = value; }
    public Long getChatModelConfigurationRevision() { return chatModelConfigurationRevision; }
    public void setChatModelConfigurationRevision(Long value) { chatModelConfigurationRevision = value; }
    public Long getChatCatalogEntryId() { return chatCatalogEntryId; }
    public void setChatCatalogEntryId(Long value) { chatCatalogEntryId = value; }
    public Long getChatCatalogEntryRevision() { return chatCatalogEntryRevision; }
    public void setChatCatalogEntryRevision(Long value) { chatCatalogEntryRevision = value; }
    public Long getEmbeddingModelConfigurationId() { return embeddingModelConfigurationId; }
    public void setEmbeddingModelConfigurationId(Long value) { embeddingModelConfigurationId = value; }
    public Long getEmbeddingModelConfigurationRevision() { return embeddingModelConfigurationRevision; }
    public void setEmbeddingModelConfigurationRevision(Long value) { embeddingModelConfigurationRevision = value; }
    public Long getEmbeddingCatalogEntryId() { return embeddingCatalogEntryId; }
    public void setEmbeddingCatalogEntryId(Long value) { embeddingCatalogEntryId = value; }
    public Long getEmbeddingCatalogEntryRevision() { return embeddingCatalogEntryRevision; }
    public void setEmbeddingCatalogEntryRevision(Long value) { embeddingCatalogEntryRevision = value; }
    public String getSystemInstruction() { return systemInstruction; }
    public void setSystemInstruction(String value) { systemInstruction = value; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
    public int getRetrievalLimit() { return retrievalLimit; }
    public void setRetrievalLimit(int value) { retrievalLimit = value; }
    public double getMinimumScore() { return minimumScore; }
    public void setMinimumScore(double value) { minimumScore = value; }
    public String getCitationPolicy() { return citationPolicy; }
    public void setCitationPolicy(String value) { citationPolicy = value; }
    public String getRefusalPolicy() { return refusalPolicy; }
    public void setRefusalPolicy(String value) { refusalPolicy = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { createdBy = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant value) { publishedAt = value; }
}
