package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 同步产物的持久化实体。
 */
@TableName("knowledge_data.sync_artifact")
public class SyncArtifactEntity {

    @TableId(value = "id", type = IdType.INPUT) private Long id;
    @TableField("tenant_id") private Long tenantId;
    @TableField("knowledge_space_id") private Long knowledgeSpaceId;
    @TableField("data_source_id") private Long dataSourceId;
    @TableField("source_revision") private long sourceRevision;
    @TableField("content_digest") private String contentDigest;
    @TableField("content_version") private int contentVersion;
    @TableField("content_object_key") private String contentObjectKey;
    @TableField("content_byte_size") private long contentByteSize;
    @TableField("content_media_type") private String contentMediaType;
    @TableField("content_created_at") private Instant contentCreatedAt;
    @TableField("record_count") private long recordCount;
    @TableField("synchronized_at") private Instant synchronizedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long knowledgeSpaceId) { this.knowledgeSpaceId = knowledgeSpaceId; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }
    public long getSourceRevision() { return sourceRevision; }
    public void setSourceRevision(long sourceRevision) { this.sourceRevision = sourceRevision; }
    public String getContentDigest() { return contentDigest; }
    public void setContentDigest(String contentDigest) { this.contentDigest = contentDigest; }
    public int getContentVersion() { return contentVersion; }
    public void setContentVersion(int contentVersion) { this.contentVersion = contentVersion; }
    public String getContentObjectKey() { return contentObjectKey; }
    public void setContentObjectKey(String contentObjectKey) { this.contentObjectKey = contentObjectKey; }
    public long getContentByteSize() { return contentByteSize; }
    public void setContentByteSize(long contentByteSize) { this.contentByteSize = contentByteSize; }
    public String getContentMediaType() { return contentMediaType; }
    public void setContentMediaType(String contentMediaType) { this.contentMediaType = contentMediaType; }
    public Instant getContentCreatedAt() { return contentCreatedAt; }
    public void setContentCreatedAt(Instant contentCreatedAt) { this.contentCreatedAt = contentCreatedAt; }
    public long getRecordCount() { return recordCount; }
    public void setRecordCount(long recordCount) { this.recordCount = recordCount; }
    public Instant getSynchronizedAt() { return synchronizedAt; }
    public void setSynchronizedAt(Instant synchronizedAt) { this.synchronizedAt = synchronizedAt; }
}
