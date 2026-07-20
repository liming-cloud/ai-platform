package com.aiplatform.appstudio.infrastructure.persistence.entity;

import java.time.Instant;

public class AppApplicationReleasePersistenceEntity {
    private Long id;
    private Long tenantId;
    private Long applicationId;
    private Long versionNo;
    private Long sourceReleaseId;
    private String requestId;
    private String operation;
    private String applicationType;
    private String applicationName;
    private String description;
    private Long modelConfigId;
    private String systemPrompt;
    private String knowledgeBaseIdsJson;
    private String toolIdsJson;
    private String ownerUserIdsJson;
    private String allowedUserIdsJson;
    private Instant applicationCreatedAt;
    private Long publishedBy;
    private String reason;
    private String traceId;
    private Instant publishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
    public Long getSourceReleaseId() { return sourceReleaseId; }
    public void setSourceReleaseId(Long sourceReleaseId) { this.sourceReleaseId = sourceReleaseId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getApplicationType() { return applicationType; }
    public void setApplicationType(String applicationType) { this.applicationType = applicationType; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getModelConfigId() { return modelConfigId; }
    public void setModelConfigId(Long modelConfigId) { this.modelConfigId = modelConfigId; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getKnowledgeBaseIdsJson() { return knowledgeBaseIdsJson; }
    public void setKnowledgeBaseIdsJson(String knowledgeBaseIdsJson) { this.knowledgeBaseIdsJson = knowledgeBaseIdsJson; }
    public String getToolIdsJson() { return toolIdsJson; }
    public void setToolIdsJson(String toolIdsJson) { this.toolIdsJson = toolIdsJson; }
    public String getOwnerUserIdsJson() { return ownerUserIdsJson; }
    public void setOwnerUserIdsJson(String ownerUserIdsJson) { this.ownerUserIdsJson = ownerUserIdsJson; }
    public String getAllowedUserIdsJson() { return allowedUserIdsJson; }
    public void setAllowedUserIdsJson(String allowedUserIdsJson) { this.allowedUserIdsJson = allowedUserIdsJson; }
    public Instant getApplicationCreatedAt() { return applicationCreatedAt; }
    public void setApplicationCreatedAt(Instant applicationCreatedAt) { this.applicationCreatedAt = applicationCreatedAt; }
    public Long getPublishedBy() { return publishedBy; }
    public void setPublishedBy(Long publishedBy) { this.publishedBy = publishedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
