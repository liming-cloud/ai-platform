package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 对象存储来源修订中冻结对象元数据的持久化实体。
 */
@TableName("knowledge_data.source_revision_object")
public class ObjectStorageObjectEntity {

    @TableField("tenant_id") private Long tenantId;
    @TableField("knowledge_space_id") private Long knowledgeSpaceId;
    @TableField("data_source_id") private Long dataSourceId;
    private long revision;
    @TableField("object_ordinal") private int objectOrdinal;
    @TableField("platform_object_id") private String platformObjectId;
    @TableField("content_digest") private String contentDigest;
    @TableField("byte_size") private long byteSize;
    @TableField("media_type") private String mediaType;
    @TableField("fresh_at") private Instant freshAt;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long knowledgeSpaceId) { this.knowledgeSpaceId = knowledgeSpaceId; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public int getObjectOrdinal() { return objectOrdinal; }
    public void setObjectOrdinal(int objectOrdinal) { this.objectOrdinal = objectOrdinal; }
    public String getPlatformObjectId() { return platformObjectId; }
    public void setPlatformObjectId(String platformObjectId) { this.platformObjectId = platformObjectId; }
    public String getContentDigest() { return contentDigest; }
    public void setContentDigest(String contentDigest) { this.contentDigest = contentDigest; }
    public long getByteSize() { return byteSize; }
    public void setByteSize(long byteSize) { this.byteSize = byteSize; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public Instant getFreshAt() { return freshAt; }
    public void setFreshAt(Instant freshAt) { this.freshAt = freshAt; }
}
