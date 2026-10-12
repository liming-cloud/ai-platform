package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 连接不可变发布事实的数据库映射，仅基础设施可见。 */
@TableName("model_runtime.platform_provider_connection_publication")
public class PlatformProviderConnectionPublicationEntity {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long connectionId;
    private Long revision;
    private Long connectionRevision;
    private Long publishedBySubjectId;
    private Instant probedAt;
    private Instant publishedAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long value) { this.connectionId = value; }
    public Long getRevision() { return revision; }
    public void setRevision(Long value) { this.revision = value; }
    public Long getConnectionRevision() { return connectionRevision; }
    public void setConnectionRevision(Long value) { this.connectionRevision = value; }
    public Long getPublishedBySubjectId() { return publishedBySubjectId; }
    public void setPublishedBySubjectId(Long value) { this.publishedBySubjectId = value; }
    public Instant getProbedAt() { return probedAt; }
    public void setProbedAt(Instant value) { this.probedAt = value; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant value) { this.publishedAt = value; }
}
