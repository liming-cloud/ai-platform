package com.aiplatform.foundation.assistantdelivery.adapter.out.persistence;

import java.time.Instant;

/**
 * 助手版本数据库行。
 */
public final class AssistantVersionRow {

    private String tenantId;
    private String id;
    private String assistantId;
    private int versionNumber;
    private String chatDeploymentId;
    private long chatDeploymentRevision;
    private int chatDeploymentOutputLimit;
    private String knowledgeSpaceId;
    private String indexVersionId;
    private long indexVersionRevision;
    private String embeddingDeploymentId;
    private long embeddingDeploymentRevision;
    private int embeddingDimension;
    private String systemInstruction;
    private int maxOutputTokens;
    private int retrievalLimit;
    private double minimumScore;
    private String citationPolicy;
    private String refusalPolicy;
    private String status;
    private long version;
    private String createdBy;
    private Instant createdAt;
    private Instant publishedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String value) { tenantId = value; }
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getAssistantId() { return assistantId; }
    public void setAssistantId(String value) { assistantId = value; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int value) { versionNumber = value; }
    public String getChatDeploymentId() { return chatDeploymentId; }
    public void setChatDeploymentId(String value) { chatDeploymentId = value; }
    public long getChatDeploymentRevision() { return chatDeploymentRevision; }
    public void setChatDeploymentRevision(long value) { chatDeploymentRevision = value; }
    public int getChatDeploymentOutputLimit() { return chatDeploymentOutputLimit; }
    public void setChatDeploymentOutputLimit(int value) { chatDeploymentOutputLimit = value; }
    public String getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(String value) { knowledgeSpaceId = value; }
    public String getIndexVersionId() { return indexVersionId; }
    public void setIndexVersionId(String value) { indexVersionId = value; }
    public long getIndexVersionRevision() { return indexVersionRevision; }
    public void setIndexVersionRevision(long value) { indexVersionRevision = value; }
    public String getEmbeddingDeploymentId() { return embeddingDeploymentId; }
    public void setEmbeddingDeploymentId(String value) { embeddingDeploymentId = value; }
    public long getEmbeddingDeploymentRevision() { return embeddingDeploymentRevision; }
    public void setEmbeddingDeploymentRevision(long value) { embeddingDeploymentRevision = value; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int value) { embeddingDimension = value; }
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
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant value) { publishedAt = value; }
}
