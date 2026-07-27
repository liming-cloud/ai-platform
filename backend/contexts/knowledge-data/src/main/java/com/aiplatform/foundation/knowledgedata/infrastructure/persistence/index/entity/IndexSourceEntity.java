package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 索引冻结来源持久化实体。
 */
@TableName("knowledge_data.index_version_source")
public class IndexSourceEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("knowledge_space_id")
    private Long knowledgeSpaceId;
    @TableField("index_version_id")
    private Long indexVersionId;
    @TableField("data_source_id")
    private Long dataSourceId;
    @TableField("content_version")
    private int contentVersion;
    @TableField("object_key")
    private String objectKey;
    @TableField("sha256")
    private String sha256;
    @TableField("byte_size")
    private long byteSize;
    @TableField("media_type")
    private String mediaType;
    @TableField("content_created_at")
    private Instant contentCreatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long value) { knowledgeSpaceId = value; }
    public Long getIndexVersionId() { return indexVersionId; }
    public void setIndexVersionId(Long value) { indexVersionId = value; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }
    public int getContentVersion() { return contentVersion; }
    public void setContentVersion(int version) { contentVersion = version; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public long getByteSize() { return byteSize; }
    public void setByteSize(long byteSize) { this.byteSize = byteSize; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public Instant getContentCreatedAt() { return contentCreatedAt; }
    public void setContentCreatedAt(Instant value) { contentCreatedAt = value; }
}
