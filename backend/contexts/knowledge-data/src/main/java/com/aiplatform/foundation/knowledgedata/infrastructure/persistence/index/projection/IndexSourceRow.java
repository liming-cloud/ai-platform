package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.projection;

import java.time.Instant;

/**
 * 索引冻结来源数据库行。
 */
public final class IndexSourceRow {

    private String dataSourceId;
    private Integer contentVersion;
    private String sourceType;
    private long sourceRevision;
    private Long syncArtifactId;
    private String qualityStatus;
    private String qualityRuleSetVersion;
    private Instant publishedAt;
    private String objectKey;
    private String sha256;
    private long byteSize;
    private String mediaType;
    private Instant contentCreatedAt;

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public Integer getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(Integer contentVersion) {
        this.contentVersion = contentVersion;
    }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { sourceType = value; }
    public long getSourceRevision() { return sourceRevision; }
    public void setSourceRevision(long value) { sourceRevision = value; }
    public Long getSyncArtifactId() { return syncArtifactId; }
    public void setSyncArtifactId(Long value) { syncArtifactId = value; }
    public String getQualityStatus() { return qualityStatus; }
    public void setQualityStatus(String value) { qualityStatus = value; }
    public String getQualityRuleSetVersion() { return qualityRuleSetVersion; }
    public void setQualityRuleSetVersion(String value) { qualityRuleSetVersion = value; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant value) { publishedAt = value; }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public long getByteSize() {
        return byteSize;
    }

    public void setByteSize(long byteSize) {
        this.byteSize = byteSize;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Instant getContentCreatedAt() {
        return contentCreatedAt;
    }

    public void setContentCreatedAt(Instant contentCreatedAt) {
        this.contentCreatedAt = contentCreatedAt;
    }
}
