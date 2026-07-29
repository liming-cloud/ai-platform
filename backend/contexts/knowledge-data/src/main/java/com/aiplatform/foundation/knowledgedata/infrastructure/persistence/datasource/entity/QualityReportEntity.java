package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 质量结论的持久化实体；不保存原始正文或连接凭据。
 */
@TableName("knowledge_data.quality_report")
public class QualityReportEntity {

    @TableId(value = "artifact_id", type = IdType.INPUT) private Long artifactId;
    @TableField("tenant_id") private Long tenantId;
    @TableField("knowledge_space_id") private Long knowledgeSpaceId;
    @TableField("rule_set_version") private String ruleSetVersion;
    private String status;
    @TableField("warning_count") private long warningCount;
    @TableField("blocking_count") private long blockingCount;

    public Long getArtifactId() { return artifactId; }
    public void setArtifactId(Long artifactId) { this.artifactId = artifactId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long knowledgeSpaceId) { this.knowledgeSpaceId = knowledgeSpaceId; }
    public String getRuleSetVersion() { return ruleSetVersion; }
    public void setRuleSetVersion(String ruleSetVersion) { this.ruleSetVersion = ruleSetVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getWarningCount() { return warningCount; }
    public void setWarningCount(long warningCount) { this.warningCount = warningCount; }
    public long getBlockingCount() { return blockingCount; }
    public void setBlockingCount(long blockingCount) { this.blockingCount = blockingCount; }
}
