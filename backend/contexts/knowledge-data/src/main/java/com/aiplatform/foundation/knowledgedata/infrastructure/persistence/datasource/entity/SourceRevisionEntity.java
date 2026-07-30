package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 不可变来源定义修订的持久化实体。
 */
@TableName("knowledge_data.source_revision")
public class SourceRevisionEntity {

    @TableField("tenant_id") private Long tenantId;
    @TableField("knowledge_space_id") private Long knowledgeSpaceId;
    @TableField("data_source_id") private Long dataSourceId;
    private long revision;
    @TableField("source_type") private String sourceType;
    @TableField("definition_digest") private String definitionDigest;
    @TableField("object_key") private String objectKey;
    @TableField("content_digest") private String contentDigest;
    @TableField("byte_size") private Long byteSize;
    @TableField("media_type") private String mediaType;
    @TableField("object_range_id") private String objectRangeId;
    @TableField("object_selection_revision") private Long objectSelectionRevision;
    @TableField("object_collection_digest") private String objectCollectionDigest;
    @TableField("created_by") private Long createdBy;
    @TableField("created_at") private Instant createdAt;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long knowledgeSpaceId) { this.knowledgeSpaceId = knowledgeSpaceId; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getDefinitionDigest() { return definitionDigest; }
    public void setDefinitionDigest(String definitionDigest) { this.definitionDigest = definitionDigest; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getContentDigest() { return contentDigest; }
    public void setContentDigest(String contentDigest) { this.contentDigest = contentDigest; }
    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getObjectRangeId() { return objectRangeId; }
    public void setObjectRangeId(String objectRangeId) { this.objectRangeId = objectRangeId; }
    public Long getObjectSelectionRevision() { return objectSelectionRevision; }
    public void setObjectSelectionRevision(Long objectSelectionRevision) {
        this.objectSelectionRevision = objectSelectionRevision;
    }
    public String getObjectCollectionDigest() { return objectCollectionDigest; }
    public void setObjectCollectionDigest(String objectCollectionDigest) {
        this.objectCollectionDigest = objectCollectionDigest;
    }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
