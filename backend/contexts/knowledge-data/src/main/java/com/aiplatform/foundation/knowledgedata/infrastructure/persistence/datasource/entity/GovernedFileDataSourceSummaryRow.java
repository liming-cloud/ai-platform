package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity;

import java.time.Instant;

/** FILE 来源列表查询的持久化投影，不映射正文、对象位置或凭据。 */
public class GovernedFileDataSourceSummaryRow {

    private Long dataSourceId;
    private Long knowledgeSpaceId;
    private String displayName;
    private String status;
    private Long version;
    private String failedOperation;
    private Long publishedArtifactId;
    private String publishedQualityStatus;
    private Instant publishedAt;

    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long knowledgeSpaceId) { this.knowledgeSpaceId = knowledgeSpaceId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getFailedOperation() { return failedOperation; }
    public void setFailedOperation(String failedOperation) { this.failedOperation = failedOperation; }
    public Long getPublishedArtifactId() { return publishedArtifactId; }
    public void setPublishedArtifactId(Long publishedArtifactId) { this.publishedArtifactId = publishedArtifactId; }
    public String getPublishedQualityStatus() { return publishedQualityStatus; }
    public void setPublishedQualityStatus(String publishedQualityStatus) {
        this.publishedQualityStatus = publishedQualityStatus;
    }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
