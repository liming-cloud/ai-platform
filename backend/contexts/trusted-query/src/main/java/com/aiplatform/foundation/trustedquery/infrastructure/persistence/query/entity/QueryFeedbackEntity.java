package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 查询反馈最小事实持久化实体。
 */
@TableName("trusted_query.query_feedback")
public class QueryFeedbackEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("query_run_id")
    private Long queryRunId;
    @TableField("requested_by")
    private Long requestedBy;
    @TableField("outcome")
    private String outcome;
    @TableField("reason")
    private String reason;
    @TableField("created_at")
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getQueryRunId() { return queryRunId; }
    public void setQueryRunId(Long value) { queryRunId = value; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long value) { requestedBy = value; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String value) { outcome = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
