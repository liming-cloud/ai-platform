package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * 查询运行持久化实体。
 */
@TableName("trusted_query.query_run")
public class QueryRunEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("assistant_version_id")
    private Long assistantVersionId;
    @TableField("requested_by")
    private Long requestedBy;
    @TableField("question_digest")
    private String questionDigest;
    @TableField("quota_period_start")
    private Instant quotaPeriodStart;
    @TableField("quota_period_end")
    private Instant quotaPeriodEnd;
    @TableField("daily_query_limit")
    private int dailyQueryLimit;
    @TableField("daily_query_ordinal")
    private int dailyQueryOrdinal;
    @TableField("concurrent_query_limit")
    private int concurrentQueryLimit;
    @TableField("accepted_lease_until")
    private Instant acceptedLeaseUntil;
    @TableField("running_lease_until")
    private Instant runningLeaseUntil;
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
    @Version
    @TableField("version")
    private long version;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("completed_at")
    private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getAssistantVersionId() { return assistantVersionId; }
    public void setAssistantVersionId(Long value) { assistantVersionId = value; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
    public String getQuestionDigest() { return questionDigest; }
    public void setQuestionDigest(String value) { questionDigest = value; }
    public Instant getQuotaPeriodStart() { return quotaPeriodStart; }
    public void setQuotaPeriodStart(Instant value) { quotaPeriodStart = value; }
    public Instant getQuotaPeriodEnd() { return quotaPeriodEnd; }
    public void setQuotaPeriodEnd(Instant value) { quotaPeriodEnd = value; }
    public int getDailyQueryLimit() { return dailyQueryLimit; }
    public void setDailyQueryLimit(int value) { dailyQueryLimit = value; }
    public int getDailyQueryOrdinal() { return dailyQueryOrdinal; }
    public void setDailyQueryOrdinal(int value) { dailyQueryOrdinal = value; }
    public int getConcurrentQueryLimit() { return concurrentQueryLimit; }
    public void setConcurrentQueryLimit(int value) { concurrentQueryLimit = value; }
    public Instant getAcceptedLeaseUntil() { return acceptedLeaseUntil; }
    public void setAcceptedLeaseUntil(Instant value) { acceptedLeaseUntil = value; }
    public Instant getRunningLeaseUntil() { return runningLeaseUntil; }
    public void setRunningLeaseUntil(Instant value) { runningLeaseUntil = value; }
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
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { completedAt = value; }
}
