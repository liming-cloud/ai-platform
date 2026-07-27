package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 文本内容版本持久化实体。
 */
@TableName("knowledge_data.text_data_source_content")
public class TextContentEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("knowledge_space_id")
    private Long knowledgeSpaceId;
    @TableField("data_source_id")
    private Long dataSourceId;
    @TableField("version")
    private int version;
    @TableField("object_key")
    private String objectKey;
    @TableField("sha256")
    private String sha256;
    @TableField("byte_size")
    private long byteSize;
    @TableField("media_type")
    private String mediaType;
    @TableField("created_at")
    private Instant createdAt;

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

    public Long getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public void setKnowledgeSpaceId(Long knowledgeSpaceId) {
        this.knowledgeSpaceId = knowledgeSpaceId;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
