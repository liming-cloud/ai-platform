package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 发布决定关系表实体；只保存冻结标识、策略版本、结论和原因码。
 */
@TableName("evaluation_operations.release_decision")
public class ReleaseDecisionEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id") private Long tenantId;
    @TableField("evaluation_run_id") private Long evaluationRunId;
    @TableField("assistant_version_id") private Long assistantVersionId;
    @TableField("assistant_version_revision") private long assistantVersionRevision;
    @TableField("policy_version") private String policyVersion;
    @TableField("status") private String status;
    @TableField("reason_code") private String reasonCode;
    @TableField("version") private long version;
    @TableField("decided_at") private Instant decidedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getEvaluationRunId() { return evaluationRunId; }
    public void setEvaluationRunId(Long value) { evaluationRunId = value; }
    public Long getAssistantVersionId() { return assistantVersionId; }
    public void setAssistantVersionId(Long value) { assistantVersionId = value; }
    public long getAssistantVersionRevision() { return assistantVersionRevision; }
    public void setAssistantVersionRevision(long value) { assistantVersionRevision = value; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String value) { policyVersion = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { reasonCode = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant value) { decidedAt = value; }
}
