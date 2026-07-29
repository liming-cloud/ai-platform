package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 已发布同步产物的持久化审计事实。 */
@TableName("knowledge_data.published_sync_artifact")
public class PublishedSyncArtifactEntity {
    @TableField("tenant_id") private Long tenantId;
    @TableField("knowledge_space_id") private Long knowledgeSpaceId;
    @TableField("data_source_id") private Long dataSourceId;
    @TableField("source_version") private long sourceVersion;
    @TableField("artifact_id") private Long artifactId;
    @TableField("quality_status") private String qualityStatus;
    @TableField("rule_set_version") private String ruleSetVersion;
    @TableField("published_at") private Instant publishedAt;
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long value) { knowledgeSpaceId = value; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long value) { dataSourceId = value; }
    public long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(long value) { sourceVersion = value; }
    public Long getArtifactId() { return artifactId; }
    public void setArtifactId(Long value) { artifactId = value; }
    public String getQualityStatus() { return qualityStatus; }
    public void setQualityStatus(String value) { qualityStatus = value; }
    public String getRuleSetVersion() { return ruleSetVersion; }
    public void setRuleSetVersion(String value) { ruleSetVersion = value; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant value) { publishedAt = value; }
}
