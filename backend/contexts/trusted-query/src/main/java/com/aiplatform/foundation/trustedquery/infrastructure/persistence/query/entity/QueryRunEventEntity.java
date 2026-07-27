package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 查询运行事件持久化实体。
 */
@TableName("trusted_query.query_run_event")
public class QueryRunEventEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("query_run_id")
    private Long queryRunId;
    @TableField("sequence")
    private long sequence;
    @TableField("event_type")
    private String eventType;
    @TableField("status")
    private String status;
    @TableField("answer")
    private String answer;
    @TableField("refusal_reason")
    private String refusalReason;
    @TableField("input_tokens")
    private int inputTokens;
    @TableField("output_tokens")
    private int outputTokens;
    @TableField("total_tokens")
    private int totalTokens;
    @TableField("occurred_at")
    private Instant occurredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getQueryRunId() { return queryRunId; }
    public void setQueryRunId(Long queryRunId) { this.queryRunId = queryRunId; }
    public long getSequence() { return sequence; }
    public void setSequence(long sequence) { this.sequence = sequence; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getRefusalReason() { return refusalReason; }
    public void setRefusalReason(String value) { refusalReason = value; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int value) { outputTokens = value; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
